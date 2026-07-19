package com.raamses.console.data

import com.raamses.console.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

/**
 * RAAMSES Gateway Client — connects to Python emulator or C# server.
 * Supports:
 *   - HTTP REST (C# server: /api/status, /api/instructions)
 *   - HTTP stats (stats-server: /stats)
 *   - Raw TCP (Python raamses protocol)
 *   - XML + JSON message encoding
 */
class RaamsesGatewayClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceId = "android-console-${UUID.randomUUID().toString().take(8)}"

    // ── State flows ──

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentStatus>>(emptyList())
    val agents: StateFlow<List<AgentStatus>> = _agents.asStateFlow()

    private val _alerts = MutableStateFlow<List<ConsoleAlert>>(emptyList())
    val alerts: StateFlow<List<ConsoleAlert>> = _alerts.asStateFlow()

    private val _serverHealth = MutableStateFlow(
        ServerHealth(0f, 0f, 0f, "", 0, 0, 0)
    )
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    private val _gatewayMessages = MutableStateFlow<List<GatewayMessage>>(emptyList())
    val gatewayMessages: StateFlow<List<GatewayMessage>> = _gatewayMessages.asStateFlow()

    // ── Internal ──

    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var statsJob: Job? = null
    private var connectionConfig: GatewayConnection = GatewayConnection()

    fun connect(config: GatewayConnection) {
        disconnect()
        connectionConfig = config

        connectionJob = scope.launch {
            try {
                _connectionState.value = _connectionState.value.copy(
                    connected = false, host = config.host, port = config.port
                )

                when {
                    // Try TCP first (Python raamses protocol)
                    config.port == 42000 || config.api_path.contains("tcp") -> {
                        connectTcp(config)
                    }
                    // HTTP REST (C# server or stats-server)
                    else -> {
                        connectHttp(config)
                    }
                }

                _connectionState.value = _connectionState.value.copy(connected = true)

                // Start heartbeat
                startHeartbeat(config)

                // Start stats polling
                startStatsPolling(config)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(connected = false)
                // Fall back to mock
                MockDataProvider().let { mock ->
                    scope.launch { mock.agents.collect { _agents.value = it } }
                    scope.launch { mock.alerts.collect { _alerts.value = it } }
                    scope.launch { mock.serverHealth.collect { _serverHealth.value = it } }
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        heartbeatJob?.cancel()
        statsJob?.cancel()
        _connectionState.value = ConnectionState()
    }

    fun sendCommand(command: String): GatewayMessage {
        val msg = GatewayMessage(
            id = UUID.randomUUID().toString(),
            text = command,
            isFromUser = true,
            timestampSec = System.currentTimeMillis() / 1000,
            command = command.split(" ").firstOrNull()
        )
        _gatewayMessages.value = _gatewayMessages.value + msg

        // Send to server
        scope.launch {
            try {
                val response = executeCommand(command)
                _gatewayMessages.value = _gatewayMessages.value + response
            } catch (e: Exception) {
                _gatewayMessages.value = _gatewayMessages.value + GatewayMessage(
                    id = UUID.randomUUID().toString(),
                    text = "Error: ${e.message}",
                    isFromUser = false,
                    timestampSec = System.currentTimeMillis() / 1000,
                    isError = true
                )
            }
        }

        return msg
    }

    fun sendChat(text: String): GatewayMessage {
        return sendCommand("/tell all $text")
    }

    // ── HTTP connection ──

    private suspend fun connectHttp(config: GatewayConnection) = withContext(Dispatchers.IO) {
        val baseUrl = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"

        // Fetch stats to verify connectivity
        try {
            val statsUrl = URL("$baseUrl${config.stats_path}")
            val conn = statsUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                parseServerStatus(body)
            }
            conn.disconnect()
        } catch (_: Exception) {
            // Stats server might not be running; try API endpoint
            try {
                val apiUrl = URL("$baseUrl${config.api_path}/status")
                val conn = apiUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val registerPayload = json.encodeToString(
                    RegisterMessage.serializer(),
                    RegisterMessage(
                        device_id = deviceId,
                        device_type = "android_console",
                        device_name = "RAAMSES Android Console",
                        capabilities = Capabilities(
                            screen = ScreenCapability(1080, 2400, 32, "oled", 400),
                            input = InputCapability(has_touch = true),
                            output = OutputCapability(has_vibration = true),
                            power = PowerCapability(has_battery = true)
                        )
                    )
                )

                OutputStreamWriter(conn.outputStream).use { it.write(registerPayload) }
                conn.connect()

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    parseRegisterAck(body)
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Neither endpoint responding
            }
        }
    }

    // ── TCP connection (raw socket for Python protocol) ──

    private suspend fun connectTcp(config: GatewayConnection) = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.connect(InetSocketAddress(config.host, config.port), 5000)

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = OutputStreamWriter(socket.getOutputStream())

        // Send register
        val register = RegisterMessage(
            device_id = deviceId,
            schema_version = "1.0",
            device_type = "android_console",
            device_name = "RAAMSES Android Console",
            capabilities = Capabilities(
                screen = ScreenCapability(1080, 2400, 32, "oled", 400),
                input = InputCapability(has_touch = true),
                output = OutputCapability(has_vibration = true),
                power = PowerCapability(has_battery = true)
            )
        )
        val envelope = RaamsesEnvelope(
            header = RaamsesHeader(
                message_id = UUID.randomUUID().toString(),
                timestamp = java.time.Instant.now().toString(),
                device_id = deviceId
            ),
            payload = json.encodeToString(RegisterMessage.serializer(), register),
            content_type = "application/json"
        )
        writer.write(json.encodeToString(RaamsesEnvelope.serializer(), envelope) + "\n")
        writer.flush()

        // Read ack
        val line = reader.readLine() ?: throw Exception("No register ack received")
        parseMessage(line)

        // Read loop
        while (isActive) {
            val msg = reader.readLine() ?: break
            parseMessage(msg)
        }
    }

    // ── Heartbeat ──

    private fun startHeartbeat(config: GatewayConnection) {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000)
                try {
                    val baseUrl = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
                    val url = URL("$baseUrl${config.api_path}/status")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    val heartbeat = mapOf(
                        "device_id" to deviceId,
                        "uptime_seconds" to (System.currentTimeMillis() / 1000),
                        "status" to "online"
                    )
                    OutputStreamWriter(conn.outputStream).use {
                        it.write(json.encodeToString(MapSerializer, heartbeat))
                    }
                    conn.connect()
                    conn.disconnect()
                } catch (_: Exception) { /* heartbeat failed, will retry */ }
            }
        }
    }

    // ── Stats polling ──

    private fun startStatsPolling(config: GatewayConnection) {
        statsJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val baseUrl = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
                    val url = URL("$baseUrl${config.stats_path}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        parseServerStatus(body)
                    }
                    conn.disconnect()
                } catch (_: Exception) { /* stats unavailable */ }
            }
        }
    }

    // ── Command execution ──

    private suspend fun executeCommand(command: String): GatewayMessage = withContext(Dispatchers.IO) {
        val parts = command.split(" ", limit = 3)
        val cmd = parts.getOrElse(0) { "" }
        val arg = parts.getOrElse(1) { "" }
        val rest = parts.getOrElse(2) { "" }
        val now = System.currentTimeMillis() / 1000

        // Try sending to server first
        if (_connectionState.value.connected) {
            try {
                val baseUrl = "http${if (connectionConfig.use_tls) "s" else ""}://${connectionConfig.host}:${connectionConfig.port}"
                val url = URL("$baseUrl${connectionConfig.api_path}/instructions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 5000

                val cmdPayload = mapOf(
                    "device_id" to deviceId,
                    "command" to command,
                    "markdown" to command
                )
                OutputStreamWriter(conn.outputStream).use {
                    it.write(json.encodeToString(MapSerializer, cmdPayload))
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    return@withContext GatewayMessage(
                        id = UUID.randomUUID().toString(),
                        text = body,
                        isFromUser = false,
                        timestampSec = now
                    )
                }
                conn.disconnect()
            } catch (_: Exception) { /* fall through to local handling */ }
        }

        // Local fallback processing
        val responseText = processLocalCommand(cmd, arg, rest)
        GatewayMessage(
            id = UUID.randomUUID().toString(),
            text = responseText,
            isFromUser = false,
            timestampSec = now
        )
    }

    private fun processLocalCommand(cmd: String, arg: String, rest: String): String {
        return when (cmd) {
            "/agents" -> _agents.value.joinToString("\n") { "  ${statusEmoji(it.status)} ${it.name} (${it.agentId}) — ${it.status}" }
            "/status" -> {
                val h = _serverHealth.value
                """
                RAAMSES SERVER STATUS
                ─────────────────────
                CPU:     ${(h.cpuPercent * 100).toInt()}%
                Memory:  ${(h.memoryPercent * 100).toInt()}%
                Disk:    ${(h.diskPercent * 100).toInt()}%
                Uptime:  ${h.uptimeDisplay}
                Agents:  ${h.agentCount} total, ${h.activeAgentCount} active, ${h.blockedAgentCount} blocked, ${h.flaggedAgentCount} flagged
                """.trimIndent()
            }
            "/sethome" -> {
                if (arg.isBlank()) return "  Usage: /sethome <agent_id> <path>"
                "  Home directory set for $arg. Restarting agent file watcher..."
            }
            "/help" -> helpText
            else -> "  Unknown: $cmd. Type /help for commands."
        }
    }

    // ── Message parsing ──

    private fun parseMessage(raw: String) {
        try {
            // Try JSON envelope
            val envelope = json.decodeFromString(RaamsesEnvelope.serializer(), raw)
            when {
                raw.contains("agent_update") || raw.contains("agent_id") -> {
                    val update = json.decodeFromString(AgentUpdate.serializer(), envelope.payload)
                    updateAgentState(update)
                }
                raw.contains("\"severity\"") -> {
                    val alert = json.decodeFromString(Alert.serializer(), envelope.payload)
                    _alerts.value = _alerts.value + ConsoleAlert(
                        id = envelope.header.message_id,
                        severity = alert.severity,
                        title = alert.title,
                        message = alert.message,
                        longText = alert.long_text,
                        timestampSec = System.currentTimeMillis() / 1000,
                        requiresAck = alert.requires_ack ?: false,
                        category = alert.category
                    )
                }
                raw.contains("accepted") -> {
                    parseRegisterAck(envelope.payload)
                }
            }
        } catch (_: Exception) {
            // Try direct JSON (no envelope)
            try {
                parseServerStatus(raw)
            } catch (_: Exception) { /* skip */ }
        }
    }

    private fun parseServerStatus(body: String) {
        val status = json.decodeFromString(ServerStatus.serializer(), body)
        _serverHealth.value = ServerHealth(
            cpuPercent = status.cpu_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
            memoryPercent = status.memory_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
            diskPercent = status.disk_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
            uptimeDisplay = status.server_uptime,
            agentCount = status.agents,
            activeAgentCount = status.agents,
            blockedAgentCount = 0,
            overallStatus = status.overall_status
        )
    }

    private fun parseRegisterAck(body: String) {
        try {
            val ack = json.decodeFromString(RegisterAck.serializer(), body)
            if (ack.accepted && ack.assigned_profile != null) {
                _connectionState.value = _connectionState.value.copy(
                    tier = ack.assigned_profile.tier,
                    maxAgents = ack.assigned_profile.max_agents,
                    serverDevice = ack.assigned_profile.name
                )
            }
        } catch (_: Exception) { /* skip */ }
    }

    private fun updateAgentState(update: AgentUpdate) {
        val existing = _agents.value.toMutableList()
        val idx = existing.indexOfFirst { it.agentId == update.agent_id }
        val agent = AgentStatus(
            agentId = update.agent_id,
            name = update.agent_name ?: update.agent_id,
            status = update.status,
            objective = update.objective,
            currentOperation = update.current_operation,
            homeDirectory = update.home_directory,
            lastVerifiedWorkSecAgo = update.last_verified_work_seconds_ago,
            lastVerifiedDescription = update.last_verified_description,
            tokenUsage = update.token_usage?.let {
                TokenUsageData(it.total ?: 0, it.last_hour ?: 0, it.today ?: 0)
            },
            subAgentCount = update.sub_agent_count ?: 0,
            needsHumanInput = update.needs_human_input ?: false,
            verifiedCompletion = update.verified_completion ?: 0f,
            reportedCompletion = update.reported_completion ?: 0f,
            verification = update.verification?.let {
                VerificationInfo(
                    verified = it.verified,
                    confidence = it.confidence,
                    issues = it.issues,
                    recommendation = it.recommendation,
                    evidenceCount = it.evidence_count,
                    mode = try { VerifierMode.valueOf(it.mode.uppercase()) } catch (_: Exception) { VerifierMode.AUTO },
                    flaggedAs = it.flagged_as
                )
            }
        )
        if (idx >= 0) existing[idx] = agent else existing.add(agent)
        _agents.value = existing
    }

    // ── Help ──

    private val helpText = """
        RAAMSES GATEWAY COMMANDS
        ────────────────────────
        Agent Control:
          /agents              List all agents
          /agent <id>          Agent detail
          /approve <id>        Approve pending action
          /reject <id>         Reject pending action
          /pause <id>          Pause agent
          /resume <id>         Resume agent
          /stop <id>           Stop agent
          /restart <id>        Restart agent
        
        Status & Info:
          /status              System health
          /alerts              Active alerts
          /ack <id>            Acknowledge alert
          /tokens              Token usage
          /pulse <id>          Work pulse
          /log <id>            Activity log
        
        Commands:
          /cmd <id> <cmd>      Raw command to agent
          /tell <id> <msg>     Send message to agent
          /ask <id> <q>        Ask agent a question
          /sethome <id> <path> Set agent home directory
        
        Connection:
          /connect <host:port> Connect to server
          /disconnect          Disconnect
          /mock                Demo mode
        
        Verifier:
          /verifier <mode>     Set verifier mode (LocalLLM/FILEbased/auto/blink)
          /verify <id>         Run verification on agent
    """.trimIndent()

    companion object {
        private fun statusEmoji(status: String): String = when (status) {
            "ACTIVE" -> "🟢"
            "QUIET" -> "🟡"
            "IDLE" -> "⚪"
            "STALE" -> "🟠"
            "BLOCKED" -> "🔴"
            "UNVERIFIED" -> "🟣"
            "HALLUCINATING" -> "💀"
            "LOOPING" -> "🔁"
            else -> "⚫"
        }
    }
}

// Helper serializer for Map<String, String> — used in heartbeat/command
private object MapSerializer : kotlinx.serialization.KSerializer<Map<String, String>> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Map")
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Map<String, String>) {
        val jsonEncoder = encoder as kotlinx.serialization.json.JsonEncoder
        jsonEncoder.encodeJsonElement(kotlinx.serialization.json.buildJsonObject {
            value.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
        })
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, String> = emptyMap()
}
