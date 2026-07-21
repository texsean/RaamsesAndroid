package com.raamses.console.data.models

import org.json.JSONArray
import org.json.JSONObject

// ═══════════════════════════════════════════════
// RAAMSES Protocol — using org.json (no kotlinx.serialization)
// ═══════════════════════════════════════════════

// ── Envelope ──

data class RaamsesHeader(
    val message_id: String = "",
    val timestamp: String = "",
    val device_id: String = "",
    val schema_version: String = "1.0",
    val version: String = "1.0"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("message_id", message_id)
        put("timestamp", timestamp)
        put("device_id", device_id)
        put("schema_version", schema_version)
        put("version", version)
    }

    companion object {
        fun fromJson(json: JSONObject) = RaamsesHeader(
            message_id = json.optString("message_id"),
            timestamp = json.optString("timestamp"),
            device_id = json.optString("device_id"),
            schema_version = json.optString("schema_version", "1.0"),
            version = json.optString("version", "1.0")
        )
    }
}

// ── Capabilities ──

data class ScreenCapability(
    val width: Int = 0, val height: Int = 0,
    val color_depth: Int = 1, val refresh_type: String = "lcd", val dpi: Int = 120
) {
    fun toJson() = JSONObject().apply {
        put("width", width); put("height", height)
        put("color_depth", color_depth); put("refresh_type", refresh_type); put("dpi", dpi)
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            ScreenCapability(it.optInt("width"), it.optInt("height"),
                it.optInt("color_depth", 1), it.optString("refresh_type", "lcd"), it.optInt("dpi", 120))
        } ?: ScreenCapability()
    }
}

data class InputCapability(
    val has_touch: Boolean = false, val has_buttons: Boolean = false, val button_count: Int = 0
) {
    fun toJson() = JSONObject().apply {
        put("has_touch", has_touch); put("has_buttons", has_buttons); put("button_count", button_count)
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            InputCapability(it.optBoolean("has_touch"), it.optBoolean("has_buttons"), it.optInt("button_count"))
        } ?: InputCapability()
    }
}

data class OutputCapability(
    val has_vibration: Boolean = false, val has_led: Boolean = false, val has_speaker: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("has_vibration", has_vibration); put("has_led", has_led); put("has_speaker", has_speaker)
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            OutputCapability(it.optBoolean("has_vibration"), it.optBoolean("has_led"), it.optBoolean("has_speaker"))
        } ?: OutputCapability()
    }
}

data class PowerCapability(
    val has_battery: Boolean = false, val battery_level: Int? = null, val is_charging: Boolean? = null
) {
    fun toJson() = JSONObject().apply {
        put("has_battery", has_battery)
        battery_level?.let { put("battery_level", it) }
        is_charging?.let { put("is_charging", it) }
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            PowerCapability(it.optBoolean("has_battery"),
                if (it.has("battery_level")) it.optInt("battery_level") else null,
                if (it.has("is_charging")) it.optBoolean("is_charging") else null)
        } ?: PowerCapability()
    }
}

data class Capabilities(
    val screen: ScreenCapability? = null, val input: InputCapability? = null,
    val output: OutputCapability? = null, val power: PowerCapability? = null,
    val max_message_size: Int? = null
) {
    fun toJson() = JSONObject().apply {
        screen?.let { put("screen", it.toJson()) }
        input?.let { put("input", it.toJson()) }
        output?.let { put("output", it.toJson()) }
        power?.let { put("power", it.toJson()) }
        max_message_size?.let { put("max_message_size", it) }
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            Capabilities(
                ScreenCapability.fromJson(it.optJSONObject("screen")),
                InputCapability.fromJson(it.optJSONObject("input")),
                OutputCapability.fromJson(it.optJSONObject("output")),
                PowerCapability.fromJson(it.optJSONObject("power")),
                if (it.has("max_message_size")) it.optInt("max_message_size") else null
            )
        } ?: Capabilities()
    }
}

// ── Register ──

data class RegisterMessage(
    val device_id: String, val schema_version: String = "1.0",
    val device_type: String, val device_name: String? = null,
    val firmware_version: String? = null, val capabilities: Capabilities? = null
) {
    fun toJson() = JSONObject().apply {
        put("device_id", device_id); put("schema_version", schema_version)
        put("device_type", device_type)
        device_name?.let { put("device_name", it) }
        firmware_version?.let { put("firmware_version", it) }
        capabilities?.let { put("capabilities", it.toJson()) }
    }
    companion object {
        fun fromJson(json: JSONObject) = RegisterMessage(
            json.getString("device_id"), json.optString("schema_version", "1.0"),
            json.getString("device_type"), json.optString("device_name", null),
            json.optString("firmware_version", null), Capabilities.fromJson(json.optJSONObject("capabilities"))
        )
    }
}

data class DeviceProfile(
    val name: String, val display_type: String, val tier: String = "free",
    val max_agents: Int = 4, val features: List<String> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("name", name); put("display_type", display_type); put("tier", tier)
        put("max_agents", max_agents)
        put("features", JSONArray(features))
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            val feats = mutableListOf<String>()
            it.optJSONArray("features")?.let { arr ->
                for (i in 0 until arr.length()) feats.add(arr.getString(i))
            }
            DeviceProfile(it.getString("name"), it.getString("display_type"),
                it.optString("tier", "free"), it.optInt("max_agents", 4), feats)
        }
    }
}

data class RegisterAck(
    val accepted: Boolean, val server_time: String,
    val schema_version: String? = null, val assigned_profile: DeviceProfile? = null,
    val assigned_tier: String? = null, val error_message: String? = null,
    val heartbeat_interval_ms: Long = 8000
) {
    fun toJson() = JSONObject().apply {
        put("accepted", accepted); put("server_time", server_time)
        schema_version?.let { put("schema_version", it) }
        assigned_profile?.let { put("assigned_profile", it.toJson()) }
        assigned_tier?.let { put("assigned_tier", it) }
        error_message?.let { put("error_message", it) }
        put("heartbeat_interval_ms", heartbeat_interval_ms)
    }
    companion object {
        fun fromJson(json: JSONObject) = RegisterAck(
            json.getBoolean("accepted"), json.getString("server_time"),
            json.optString("schema_version", null),
            DeviceProfile.fromJson(json.optJSONObject("assigned_profile")),
            json.optString("assigned_tier", null),
            json.optString("error_message", null),
            json.optLong("heartbeat_interval_ms", 8000)
        )
    }
}

// ── Heartbeat ──

data class Heartbeat(
    val uptime_seconds: Long, val battery_percent: Int? = null,
    val signal_strength: Int? = null, val free_memory_kb: Long? = null,
    val cpu_temp_celsius: Float? = null
) {
    fun toJson() = JSONObject().apply {
        put("uptime_seconds", uptime_seconds)
        battery_percent?.let { put("battery_percent", it) }
        signal_strength?.let { put("signal_strength", it) }
        free_memory_kb?.let { put("free_memory_kb", it) }
        cpu_temp_celsius?.let { put("cpu_temp_celsius", it.toDouble()) }
    }
    companion object {
        fun fromJson(json: JSONObject) = Heartbeat(
            json.getLong("uptime_seconds"),
            if (json.has("battery_percent")) json.optInt("battery_percent") else null,
            if (json.has("signal_strength")) json.optInt("signal_strength") else null,
            if (json.has("free_memory_kb")) json.optLong("free_memory_kb") else null,
            if (json.has("cpu_temp_celsius")) json.optDouble("cpu_temp_celsius").toFloat() else null
        )
    }
}

// ── Alert ──

data class Alert(
    val severity: String, val title: String, val message: String,
    val long_text: String? = null, val requires_ack: Boolean? = null,
    val vibrate: Boolean? = null, val category: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("severity", severity); put("title", title); put("message", message)
        long_text?.let { put("long_text", it) }
        requires_ack?.let { put("requires_ack", it) }
        vibrate?.let { put("vibrate", it) }
        category?.let { put("category", it) }
    }
    companion object {
        fun fromJson(json: JSONObject) = Alert(
            json.getString("severity"), json.getString("title"), json.getString("message"),
            json.optString("long_text", null),
            if (json.has("requires_ack")) json.optBoolean("requires_ack") else null,
            if (json.has("vibrate")) json.optBoolean("vibrate") else null,
            json.optString("category", null)
        )
    }
}

// ── Command ──

data class Command(
    val command_id: String, val action: String,
    val payload: String? = null, val target_agent_id: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("command_id", command_id); put("action", action)
        payload?.let { put("payload", it) }
        target_agent_id?.let { put("target_agent_id", it) }
    }
    companion object {
        fun fromJson(json: JSONObject) = Command(
            json.getString("command_id"), json.getString("action"),
            json.optString("payload", null), json.optString("target_agent_id", null)
        )
    }
}

data class CommandResult(
    val command_id: String, val success: Boolean,
    val message: String? = null, val data: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("command_id", command_id); put("success", success)
        message?.let { put("message", it) }; data?.let { put("data", it) }
    }
    companion object {
        fun fromJson(json: JSONObject) = CommandResult(
            json.getString("command_id"), json.getBoolean("success"),
            json.optString("message", null), json.optString("data", null)
        )
    }
}

// ── Agent Update ──

data class TokenUsage(
    val total: Long? = null, val last_hour: Long? = null, val today: Long? = null
) {
    fun toJson() = JSONObject().apply {
        total?.let { put("total", it) }; last_hour?.let { put("last_hour", it) }; today?.let { put("today", it) }
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            TokenUsage(
                if (it.has("total")) it.optLong("total") else null,
                if (it.has("last_hour")) it.optLong("last_hour") else null,
                if (it.has("today")) it.optLong("today") else null
            )
        } ?: TokenUsage()
    }
}

data class VerificationResult(
    val verified: Boolean, val confidence: Float, val issues: List<String> = emptyList(),
    val recommendation: String? = null, val evidence_count: Int = 0,
    val mode: String = "auto", val flagged_as: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("verified", verified); put("confidence", confidence.toDouble())
        put("issues", JSONArray(issues)); put("evidence_count", evidence_count)
        put("mode", mode)
        recommendation?.let { put("recommendation", it) }
        flagged_as?.let { put("flagged_as", it) }
    }
    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            val issues = mutableListOf<String>()
            it.optJSONArray("issues")?.let { arr ->
                for (i in 0 until arr.length()) issues.add(arr.getString(i))
            }
            VerificationResult(
                it.optBoolean("verified"), it.optDouble("confidence").toFloat(), issues,
                it.optString("recommendation", null), it.optInt("evidence_count"),
                it.optString("mode", "auto"), it.optString("flagged_as", null)
            )
        }
    }
}

data class AgentUpdate(
    val agent_id: String, val status: String,
    val agent_name: String? = null, val objective: String? = null,
    val current_operation: String? = null, val home_directory: String? = null,
    val token_usage: TokenUsage? = null, val sub_agent_count: Int? = null,
    val needs_human_input: Boolean? = null,
    val verified_completion: Float? = null, val reported_completion: Float? = null,
    val last_verified_work_seconds_ago: Long? = null,
    val last_verified_description: String? = null,
    val verification: VerificationResult? = null
) {
    fun toJson() = JSONObject().apply {
        put("agent_id", agent_id); put("status", status)
        agent_name?.let { put("agent_name", it) }
        objective?.let { put("objective", it) }
        current_operation?.let { put("current_operation", it) }
        home_directory?.let { put("home_directory", it) }
        token_usage?.let { put("token_usage", it.toJson()) }
        sub_agent_count?.let { put("sub_agent_count", it) }
        needs_human_input?.let { put("needs_human_input", it) }
        verified_completion?.let { put("verified_completion", it.toDouble()) }
        reported_completion?.let { put("reported_completion", it.toDouble()) }
        last_verified_work_seconds_ago?.let { put("last_verified_work_seconds_ago", it) }
        last_verified_description?.let { put("last_verified_description", it) }
        verification?.let { put("verification", it.toJson()) }
    }
    companion object {
        fun fromJson(json: JSONObject) = AgentUpdate(
            json.getString("agent_id"), json.getString("status"),
            json.optString("agent_name", null), json.optString("objective", null),
            json.optString("current_operation", null), json.optString("home_directory", null),
            TokenUsage.fromJson(json.optJSONObject("token_usage")),
            if (json.has("sub_agent_count")) json.optInt("sub_agent_count") else null,
            if (json.has("needs_human_input")) json.optBoolean("needs_human_input") else null,
            if (json.has("verified_completion")) json.optDouble("verified_completion").toFloat() else null,
            if (json.has("reported_completion")) json.optDouble("reported_completion").toFloat() else null,
            if (json.has("last_verified_work_seconds_ago")) json.optLong("last_verified_work_seconds_ago") else null,
            json.optString("last_verified_description", null),
            VerificationResult.fromJson(json.optJSONObject("verification"))
        )
    }
}

// ── Server Status (stats server) ──

data class ServerStatus(
    val device: String = "", val timestamp: String = "",
    val agents: Int = 0, val subagents: Int = 0,
    val tokens_used: Long = 0, val tokens_limit: Long = 0,
    val tokens_today: Long = 0, val tokens_last_hour: Long = 0,
    val cpu_usage: String = "0%", val memory_usage: String = "0%",
    val disk_usage: String = "0%", val server_uptime: String = "",
    val overall_status: String = "green",
    val active_projects: Map<String, Int>? = null
) {
    companion object {
        fun fromJson(json: JSONObject) = ServerStatus(
            json.optString("device"), json.optString("timestamp"),
            json.optInt("agents"), json.optInt("subagents"),
            json.optLong("tokens_used"), json.optLong("tokens_limit"),
            json.optLong("tokens_today"), json.optLong("tokens_last_hour"),
            json.optString("cpu_usage", "0%"), json.optString("memory_usage", "0%"),
            json.optString("disk_usage", "0%"), json.optString("server_uptime"),
            json.optString("overall_status", "green"),
            json.optJSONObject("active_projects")?.let { obj ->
                val map = mutableMapOf<String, Int>()
                obj.keys().forEach { key -> map[key] = obj.optInt(key) }
                map
            }
        )
    }
}

// ── Verifier Mode ──

enum class VerifierMode(val label: String, val description: String) {
    LOCAL_LLM("LocalLLM", "Local LLM verifies agent claims against evidence"),
    FILE_BASED("FILEbased", "File/diff-based verification only"),
    AUTO("auto", "Auto-selects best available verifier"),
    BLINK("blink", "Rapid visual indicator on mismatch")
}

// ── Connection Config ──

data class GatewayConnection(
    val host: String = "localhost", val port: Int = 8765,
    val use_tls: Boolean = false, val api_path: String = "/api",
    val stats_path: String = "/stats", val auto_reconnect: Boolean = true,
    val reconnect_delay_ms: Long = 5000
)

// ── Raamses Envelope ──

data class RaamsesEnvelope(
    val header: RaamsesHeader,
    val payload: String,
    val content_type: String = "application/json"
) {
    fun toJson() = JSONObject().apply {
        put("header", header.toJson())
        put("payload", payload)
        put("content_type", content_type)
    }
    companion object {
        fun fromJson(json: JSONObject) = RaamsesEnvelope(
            header = RaamsesHeader.fromJson(json.getJSONObject("header")),
            payload = json.getString("payload"),
            content_type = json.optString("content_type", "application/json")
        )
    }
}

// ── Simple message helpers ──

fun jsonToMap(json: JSONObject): Map<String, String> {
    val map = mutableMapOf<String, String>()
    json.keys().forEach { key -> map[key] = json.optString(key, "") }
    return map
}

fun mapToJson(map: Map<String, String>): JSONObject {
    return JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }
}
