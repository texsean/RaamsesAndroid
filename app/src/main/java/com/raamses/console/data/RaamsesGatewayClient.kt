package com.raamses.console.data

import android.util.Log
import com.raamses.console.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

class RaamsesGatewayClient {

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
    private var gatewaySocket: Socket? = null
    private var gatewayWriter: OutputStreamWriter? = null
    private var gatewayReader: BufferedReader? = null

    fun connect(config: GatewayConnection) {
        disconnect()
        connectionConfig = config
        Log.d(TAG, "CONNECT → ${config.host}:${config.port} (TLS=${config.use_tls})")

        connectionJob = scope.launch {
            try {
                _connectionState.value = _connectionState.value.copy(
                    connected = false, host = config.host, port = config.port
                )

                when {
                    config.port == 8765 -> {
                        Log.d(TAG, "Using GATEWAY text protocol for port 8765")
                        connectGateway(config)
                    }
                    config.port == 42000 || config.api_path.contains("tcp") -> {
                        Log.d(TAG, "Using TCP mode for port 42000")
                        connectTcp(config)
                    }
                    else -> {
                        Log.d(TAG, "Using HTTP mode")
                        connectHttp(config)
                    }
                }

                Log.i(TAG, "CONNECTED ✓ — ${config.host}:${config.port}")
                _connectionState.value = _connectionState.value.copy(connected = true)

                startHeartbeat(config)
                startStatsPolling(config)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CONNECT FAILED ✗ — ${e.javaClass.simpleName}: ${e.message}")
                _connectionState.value = _connectionState.value.copy(connected = false)
                MockDataProvider().let { mock ->
                    scope.launch { mock.agents.collect { _agents.value = it } }
                    scope.launch { mock.alerts.collect { _alerts.value = it } }
                    scope.launch { mock.serverHealth.collect { _serverHealth.value = it } }
                }
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "DISCONNECT")
        connectionJob?.cancel()
        heartbeatJob?.cancel()
        statsJob?.cancel()
        try { gatewaySocket?.close() } catch (_: Exception) {}
        gatewaySocket = null; gatewayWriter = null; gatewayReader = null
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
        Log.d(TAG, "HTTP → trying $baseUrl${config.stats_path}")

        try {
            val statsUrl = URL("$baseUrl${config.stats_path}")
            val conn = statsUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            Log.d(TAG, "HTTP ← ${conn.responseCode} from $baseUrl${config.stats_path}")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.v(TAG, "HTTP body ($baseUrl${config.stats_path}): ${body.take(500)}")
                parseServerStatus(body)
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "HTTP stats failed: ${e.javaClass.simpleName} — trying API endpoint")
            try {
                val apiUrl = URL("$baseUrl${config.api_path}/status")
                val conn = apiUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val registerPayload = RegisterMessage(
                        device_id = deviceId,
                        device_type = "android_console",
                        device_name = "RAAMSES Android Console",
                        capabilities = Capabilities(
                            screen = ScreenCapability(1080, 2400, 32, "oled", 400),
                            input = InputCapability(has_touch = true),
                            output = OutputCapability(has_vibration = true),
                            power = PowerCapability(has_battery = true)
                        )
                    ).toJson().toString()

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
            payload = register.toJson().toString(),
            content_type = "application/json"
        )
        writer.write(envelope.toJson().toString() + "\n")
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

    // ── Gateway text protocol (port 8765) ──

    private suspend fun connectGateway(config: GatewayConnection) = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.connect(InetSocketAddress(config.host, config.port), 5000)
        gatewaySocket = socket
        val writer = OutputStreamWriter(socket.getOutputStream())
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        gatewayWriter = writer
        gatewayReader = reader

        // Register
        val deviceType = "android_console"
        val register = "REGISTER:$deviceId|$deviceType|1.0\n"
        Log.d(TAG, "GATEWAY → $register")
        writer.write(register)
        writer.flush()

        val ack = reader.readLine() ?: throw Exception("No REGISTER_ACK")
        Log.d(TAG, "GATEWAY ← $ack")
        if (ack.startsWith("REGISTER_ACK:true")) {
            val parts = ack.split("|")
            _connectionState.value = _connectionState.value.copy(
                serverVersion = parts.getOrElse(3) { "" }
            )
        }

        // Read loop — parse agent list responses
        scope.launch {
            try {
                while (isActive) {
                    val line = gatewayReader?.readLine() ?: break
                    Log.v(TAG, "GATEWAY recv: $line")
                    if (line.startsWith("Connected agents") || line.contains("type=")) {
                        parseAgentListLine(line)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GATEWAY read loop: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun sendGatewayCommand(cmd: String) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "GATEWAY → $cmd")
                gatewayWriter?.write("$cmd\n")
                gatewayWriter?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "GATEWAY send: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun parseAgentListLine(line: String) {
        // Parse: "  ● agent-xxx type=cyd task='75%...'"
        // or:    "Connected agents (3):"
        val agentPattern = Regex("""[●◐○]\s+(\S+)\s+type=(\S+)\s+task='(.+?)'""")
        agentPattern.findAll(line).forEach { match ->
            val (name, type, task) = match.destructured
            val existing = _agents.value.toMutableList()
            val idx = existing.indexOfFirst { it.agentId == name }
            val agent = AgentStatus(
                agentId = name,
                name = name,
                status = if (line.contains("●")) "ACTIVE" else "IDLE",
                objective = task,
                currentOperation = task
            )
            if (idx >= 0) existing[idx] = agent else existing.add(agent)
            _agents.value = existing
        }
    }

    // ── Heartbeat ──

    private fun startHeartbeat(config: GatewayConnection) {
        val isGateway = config.port == 8765
        Log.d(TAG, "HEARTBEAT started — every 8s → ${config.host}:${config.port} (${if (isGateway) "text" else "HTTP"})")
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000)
                try {
                    if (isGateway) {
                        sendGatewayCommand("heartbeat")
                    } else {
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
                            it.write(JSONObject(heartbeat).toString())
                        }
                        conn.connect()
                        conn.disconnect()
                    }
                } catch (e: Exception) { Log.v(TAG, "HEARTBEAT: ${e.javaClass.simpleName}") }
            }
        }
    }

    // ── Stats polling ──

    private fun startStatsPolling(config: GatewayConnection) {
        val isGateway = config.port == 8765
        Log.d(TAG, "STATS polling — every 5s → ${config.host}:${config.port} (${if (isGateway) "gateway agents" else "HTTP"})")
        statsJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    if (isGateway) {
                        sendGatewayCommand("agents")
                    } else {
                        val baseUrl = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
                        val url = URL("$baseUrl${config.stats_path}")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val body = conn.inputStream.bufferedReader().readText()
                            Log.v(TAG, "STATS ← ${body.take(300)}")
                            parseServerStatus(body)
                        }
                        conn.disconnect()
                    }
                } catch (e: Exception) { Log.v(TAG, "STATS poll: ${e.javaClass.simpleName}") }
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
                if (connectionConfig.port == 8765) {
                    // Gateway text protocol — send slash command directly
                    sendGatewayCommand(command)
                    return@withContext GatewayMessage(
                        id = UUID.randomUUID().toString(),
                        text = "Sent: $command",
                        isFromUser = false,
                        timestampSec = now
                    )
                }
                // HTTP mode
                val baseUrl = "http${if (connectionConfig.use_tls) "s" else ""}://${connectionConfig.host}:${connectionConfig.port}"
                val url = URL("$baseUrl${connectionConfig.api_path}/instructions")
                Log.d(TAG, "CMD → POST $url: $command")
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
                    it.write(JSONObject(cmdPayload).toString())
                }

                Log.d(TAG, "CMD ← ${conn.responseCode}")
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    Log.v(TAG, "CMD response: ${body.take(300)}")
                    conn.disconnect()
                    return@withContext GatewayMessage(
                        id = UUID.randomUUID().toString(),
                        text = body,
                        isFromUser = false,
                        timestampSec = now
                    )
                }
                conn.disconnect()
            } catch (e: Exception) { Log.w(TAG, "CMD failed: ${e.javaClass.simpleName}") }
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
            val envelope = RaamsesEnvelope.fromJson(JSONObject(raw))
            when {
                raw.contains("agent_update") || raw.contains("agent_id") -> {
                    val update = AgentUpdate.fromJson(JSONObject(envelope.payload))
                    updateAgentState(update)
                }
                raw.contains("\"severity\"") -> {
                    val alert = Alert.fromJson(JSONObject(envelope.payload))
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
        Log.d(TAG, "PARSE ← ServerStatus: ${body.take(200)}")
        val status = ServerStatus.fromJson(JSONObject(body))
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
            val ack = RegisterAck.fromJson(JSONObject(body))
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
        private const val TAG = "RAAMSES"
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
