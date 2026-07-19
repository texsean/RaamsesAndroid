package com.raamses.console.data

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

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentStatus>>(emptyList())
    val agents: StateFlow<List<AgentStatus>> = _agents.asStateFlow()

    private val _alerts = MutableStateFlow<List<ConsoleAlert>>(emptyList())
    val alerts: StateFlow<List<ConsoleAlert>> = _alerts.asStateFlow()

    private val _serverHealth = MutableStateFlow(ServerHealth(0f, 0f, 0f, "", 0, 0, 0))
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    private val _gatewayMessages = MutableStateFlow<List<GatewayMessage>>(emptyList())
    val gatewayMessages: StateFlow<List<GatewayMessage>> = _gatewayMessages.asStateFlow()

    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var statsJob: Job? = null
    private var connectionConfig: GatewayConnection = GatewayConnection()

    fun connect(config: GatewayConnection) {
        disconnect()
        connectionConfig = config
        connectionJob = scope.launch {
            try {
                _connectionState.value = _connectionState.value.copy(connected = false, host = config.host, port = config.port)
                if (config.port == 42000) connectTcp(config) else connectHttp(config)
                _connectionState.value = _connectionState.value.copy(connected = true)
                startHeartbeat(config)
                startStatsPolling(config)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(connected = false)
                fallbackToMock()
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel(); heartbeatJob?.cancel(); statsJob?.cancel()
        _connectionState.value = ConnectionState()
    }

    fun sendCommand(command: String): GatewayMessage {
        val msg = GatewayMessage(id = UUID.randomUUID().toString(), text = command,
            isFromUser = true, timestampSec = System.currentTimeMillis() / 1000,
            command = command.split(" ").firstOrNull())
        _gatewayMessages.value = _gatewayMessages.value + msg
        scope.launch {
            try {
                val response = executeCommand(command)
                _gatewayMessages.value = _gatewayMessages.value + response
            } catch (e: Exception) {
                _gatewayMessages.value = _gatewayMessages.value + GatewayMessage(
                    id = UUID.randomUUID().toString(), text = "Error: ${e.message}",
                    isFromUser = false, timestampSec = System.currentTimeMillis() / 1000, isError = true)
            }
        }
        return msg
    }

    fun sendChat(text: String) = sendCommand("/tell all $text")

    private fun fallbackToMock() {
        MockDataProvider().let { mock ->
            scope.launch { mock.agents.collect { _agents.value = it } }
            scope.launch { mock.alerts.collect { _alerts.value = it } }
            scope.launch { mock.serverHealth.collect { _serverHealth.value = it } }
        }
    }

    private suspend fun connectHttp(config: GatewayConnection) = withContext(Dispatchers.IO) {
        val base = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
        try {
            val conn = (URL("$base${config.stats_path}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000; connect()
            }
            if (conn.responseCode == 200) {
                parseServerStatus(conn.inputStream.bufferedReader().readText())
            }
            conn.disconnect()
        } catch (_: Exception) { /* stats optional */ }
    }

    private suspend fun connectTcp(config: GatewayConnection) = withContext(Dispatchers.IO) {
        val socket = Socket().apply { connect(InetSocketAddress(config.host, config.port), 5000) }
        val writer = OutputStreamWriter(socket.getOutputStream())
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val register = RegisterMessage(device_id = deviceId, device_type = "android_console",
            device_name = "RAAMSES Android Console", capabilities = Capabilities(
                screen = ScreenCapability(1080, 2400, 32, "oled", 400),
                input = InputCapability(has_touch = true),
                output = OutputCapability(has_vibration = true),
                power = PowerCapability(has_battery = true)))
        val envelope = JSONObject().apply {
            put("header", JSONObject().apply {
                put("message_id", UUID.randomUUID().toString())
                put("timestamp", java.time.Instant.now().toString())
                put("device_id", deviceId)
                put("schema_version", "1.0")
            })
            put("payload", register.toJson().toString())
            put("content_type", "application/json")
        }
        writer.write(envelope.toString() + "\n"); writer.flush()
        val line = reader.readLine() ?: throw Exception("No register ack")
        parseMessage(line)
        while (isActive) { reader.readLine()?.let { parseMessage(it) } ?: break }
    }

    private fun startHeartbeat(config: GatewayConnection) {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(8000)
                try {
                    val base = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
                    val conn = (URL("$base${config.api_path}/status").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
                        doOutput = true; connectTimeout = 3000; readTimeout = 3000
                    }
                    OutputStreamWriter(conn.outputStream).use {
                        it.write(JSONObject().apply {
                            put("device_id", deviceId)
                            put("uptime_seconds", System.currentTimeMillis() / 1000)
                            put("status", "online")
                        }.toString())
                    }
                    conn.connect(); conn.disconnect()
                } catch (_: Exception) { }
            }
        }
    }

    private fun startStatsPolling(config: GatewayConnection) {
        statsJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val base = "http${if (config.use_tls) "s" else ""}://${config.host}:${config.port}"
                    val conn = (URL("$base${config.stats_path}").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3000; readTimeout = 3000
                    }
                    if (conn.responseCode == 200) parseServerStatus(conn.inputStream.bufferedReader().readText())
                    conn.disconnect()
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun executeCommand(command: String): GatewayMessage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        if (_connectionState.value.connected) {
            try {
                val base = "http${if (connectionConfig.use_tls) "s" else ""}://${connectionConfig.host}:${connectionConfig.port}"
                val conn = (URL("$base${connectionConfig.api_path}/instructions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
                    doOutput = true; connectTimeout = 3000; readTimeout = 5000
                }
                OutputStreamWriter(conn.outputStream).use {
                    it.write(JSONObject().apply {
                        put("device_id", deviceId); put("command", command); put("markdown", command)
                    }.toString())
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    return@withContext GatewayMessage(id = UUID.randomUUID().toString(), text = body,
                        isFromUser = false, timestampSec = now)
                }
                conn.disconnect()
            } catch (_: Exception) { }
        }
        val parts = command.split(" ", limit = 3)
        val response = processLocalCommand(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        GatewayMessage(id = UUID.randomUUID().toString(), text = response, isFromUser = false, timestampSec = now)
    }

    private fun processLocalCommand(cmd: String, arg: String, rest: String): String = when (cmd) {
        "/agents" -> _agents.value.joinToString("\n") { "  ${statusEmoji(it.status)} ${it.name} — ${it.status}" }
        "/status" -> {
            val h = _serverHealth.value
            "CPU: ${(h.cpuPercent*100).toInt()}%  MEM: ${(h.memoryPercent*100).toInt()}%  DISK: ${(h.diskPercent*100).toInt()}%  UP: ${h.uptimeDisplay}\nAgents: ${h.agentCount} total, ${h.activeAgentCount} active, ${h.blockedAgentCount} blocked, ${h.flaggedAgentCount} flagged"
        }
        "/sethome" -> if (arg.isBlank()) "Usage: /sethome <agent_id> <path>" else "Home directory set for $arg."
        "/help" -> helpText
        else -> "Unknown: $cmd. Type /help for commands."
    }

    private fun parseMessage(raw: String) {
        try {
            val obj = JSONObject(raw)
            val payload = obj.optString("payload", raw)
            when {
                raw.contains("agent_update") || raw.contains("agent_id") -> {
                    val update = AgentUpdate.fromJson(if (raw.contains("agent_update")) JSONObject(payload) else obj)
                    updateAgentState(update)
                }
                raw.contains("\"severity\"") -> {
                    val alert = Alert.fromJson(if (raw.contains("\"severity\"")) JSONObject(payload) else obj)
                    _alerts.value = _alerts.value + ConsoleAlert(
                        id = obj.optJSONObject("header")?.optString("message_id") ?: UUID.randomUUID().toString(),
                        severity = alert.severity, title = alert.title, message = alert.message,
                        longText = alert.long_text, timestampSec = System.currentTimeMillis() / 1000,
                        requiresAck = alert.requires_ack ?: false, category = alert.category)
                }
                raw.contains("accepted") -> {
                    val ack = RegisterAck.fromJson(if (raw.contains("accepted")) JSONObject(payload) else obj)
                    if (ack.accepted && ack.assigned_profile != null) {
                        _connectionState.value = _connectionState.value.copy(
                            tier = ack.assigned_profile.tier, maxAgents = ack.assigned_profile.max_agents,
                            serverDevice = ack.assigned_profile.name)
                    }
                }
                raw.contains("cpu_usage") -> parseServerStatus(raw)
            }
        } catch (_: Exception) { }
    }

    private fun parseServerStatus(body: String) {
        try {
            val s = ServerStatus.fromJson(JSONObject(body))
            _serverHealth.value = ServerHealth(
                cpuPercent = s.cpu_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
                memoryPercent = s.memory_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
                diskPercent = s.disk_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f,
                uptimeDisplay = s.server_uptime, agentCount = s.agents, activeAgentCount = s.agents,
                blockedAgentCount = 0, overallStatus = s.overall_status)
        } catch (_: Exception) { }
    }

    private fun updateAgentState(update: AgentUpdate) {
        val existing = _agents.value.toMutableList()
        val idx = existing.indexOfFirst { it.agentId == update.agent_id }
        val agent = AgentStatus(
            agentId = update.agent_id, name = update.agent_name ?: update.agent_id,
            status = update.status, objective = update.objective,
            currentOperation = update.current_operation, homeDirectory = update.home_directory,
            lastVerifiedWorkSecAgo = update.last_verified_work_seconds_ago,
            lastVerifiedDescription = update.last_verified_description,
            tokenUsage = update.token_usage?.let { TokenUsageData(it.total ?: 0, it.last_hour ?: 0, it.today ?: 0) },
            subAgentCount = update.sub_agent_count ?: 0,
            needsHumanInput = update.needs_human_input ?: false,
            verifiedCompletion = update.verified_completion ?: 0f,
            reportedCompletion = update.reported_completion ?: 0f,
            verification = update.verification?.let {
                VerificationInfo(verified = it.verified, confidence = it.confidence,
                    issues = it.issues, recommendation = it.recommendation,
                    evidenceCount = it.evidence_count,
                    mode = try { VerifierMode.valueOf(it.mode.uppercase()) } catch (_: Exception) { VerifierMode.AUTO },
                    flaggedAs = it.flagged_as)
            })
        if (idx >= 0) existing[idx] = agent else existing.add(agent)
        _agents.value = existing
    }

    private val helpText = """
RAAMSES GATEWAY COMMANDS
Agent: /agents /agent /approve /reject /pause /resume /stop /restart
Status: /status /alerts /ack /tokens /pulse /log
Commands: /cmd /tell /ask /sethome /verify /verifier
Connection: /connect /disconnect /mock
    """.trimIndent()

    companion object {
        fun statusEmoji(status: String): String = when (status) {
            "ACTIVE" -> "\uD83D\uDFE2"; "QUIET" -> "\uD83D\uDFE1"; "IDLE" -> "\u26AA"
            "STALE" -> "\uD83D\uDFE0"; "BLOCKED" -> "\uD83D\uDD34"; "UNVERIFIED" -> "\uD83D\uDFE3"
            "HALLUCINATING" -> "\uD83D\uDC80"; "LOOPING" -> "\uD83D\uDD01"
            else -> "\u26AB"
        }
    }
}
