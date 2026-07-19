package com.raamses.console.data.models

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════
// RAAMSES Protocol — Full Schema
// Supports XML (via envelope) and JSON (native)
// Aligned with C# Ramses.Server.Domain + Python raamses/messages/
// ═══════════════════════════════════════════════

// ── Envelope (XML/JSON hybrid) ──

@Serializable
data class RaamsesHeader(
    val message_id: String,
    val timestamp: String,         // ISO 8601 UTC
    val device_id: String,
    val schema_version: String = "1.0",
    val version: String = "1.0"
)

@Serializable
data class RaamsesEnvelope(
    val header: RaamsesHeader,
    val payload: String,           // JSON or XML payload string
    val content_type: String = "application/json"  // "application/json" or "application/xml"
)

// ── Register (full capabilities) ──

@Serializable
data class ScreenCapability(
    val width: Int = 0,
    val height: Int = 0,
    val color_depth: Int = 1,
    val refresh_type: String = "lcd",    // lcd, epaper, oled
    val dpi: Int = 120
)

@Serializable
data class InputCapability(
    val has_touch: Boolean = false,
    val has_buttons: Boolean = false,
    val button_count: Int = 0
)

@Serializable
data class OutputCapability(
    val has_vibration: Boolean = false,
    val has_led: Boolean = false,
    val has_speaker: Boolean = false
)

@Serializable
data class PowerCapability(
    val has_battery: Boolean = false,
    val battery_level: Int? = null,
    val is_charging: Boolean? = null
)

@Serializable
data class Capabilities(
    val screen: ScreenCapability? = null,
    val input: InputCapability? = null,
    val output: OutputCapability? = null,
    val power: PowerCapability? = null,
    val max_message_size: Int? = null
)

@Serializable
data class RegisterMessage(
    val device_id: String,
    val schema_version: String = "1.0",
    val device_type: String,              // cyd, epaper, watch, android_console, virtual
    val device_name: String? = null,
    val firmware_version: String? = null,
    val capabilities: Capabilities? = null
)

@Serializable
data class DeviceProfile(
    val name: String,
    val display_type: String,             // CYD, ePaper, LCD, OLED
    val tier: String = "free",            // free, pro, professional, enterprise
    val max_agents: Int = 4,
    val features: List<String> = emptyList()
)

@Serializable
data class RegisterAck(
    val accepted: Boolean,
    val server_time: String,
    val schema_version: String? = null,
    val assigned_profile: DeviceProfile? = null,
    val assigned_tier: String? = null,
    val error_message: String? = null,
    val heartbeat_interval_ms: Long = 8000
)

// ── Heartbeat ──

@Serializable
data class Heartbeat(
    val uptime_seconds: Long,
    val battery_percent: Int? = null,
    val signal_strength: Int? = null,      // 0-100
    val free_memory_kb: Long? = null,
    val cpu_temp_celsius: Float? = null
)

// ── Alert (Short/Medium/Long text) ──

@Serializable
data class Alert(
    val severity: String,                  // info, warning, critical
    val title: String,                     // short — fits on pager
    val message: String,                   // medium — fits on CYD
    val long_text: String? = null,         // full detail — fits on desktop/tablet
    val requires_ack: Boolean? = null,
    val vibrate: Boolean? = null,
    val category: String? = null           // agent, system, verification, hardware
)

// ── Command + Result ──

@Serializable
data class Command(
    val command_id: String,
    val action: String,
    val payload: String? = null,
    val target_agent_id: String? = null
)

@Serializable
data class CommandResult(
    val command_id: String,
    val success: Boolean,
    val message: String? = null,
    val data: String? = null               // arbitrary JSON payload
)

// ── Agent Update ──

@Serializable
data class TokenUsage(
    val total: Long? = null,
    val last_hour: Long? = null,
    val today: Long? = null
)

@Serializable
data class AgentUpdate(
    val agent_id: String,
    val agent_name: String? = null,
    val status: String,                    // ACTIVE, QUIET, IDLE, STALE, BLOCKED, UNVERIFIED, HALLUCINATING, LOOPING
    val objective: String? = null,
    val current_operation: String? = null,
    val home_directory: String? = null,
    val token_usage: TokenUsage? = null,
    val sub_agent_count: Int? = null,
    val needs_human_input: Boolean? = null,
    val verified_completion: Float? = null,
    val reported_completion: Float? = null,
    val last_verified_work_seconds_ago: Long? = null,
    val last_verified_description: String? = null,
    val verification: VerificationResult? = null
)

// ── Verification ──

enum class VerifierMode(val label: String, val description: String) {
    LOCAL_LLM("LocalLLM", "Local LLM verifies agent claims against evidence"),
    FILE_BASED("FILEbased", "File/diff-based verification only"),
    AUTO("auto", "Auto-selects best available verifier"),
    BLINK("blink", "Rapid visual indicator — blink on mismatch")
}

@Serializable
data class VerificationResult(
    val verified: Boolean,
    val confidence: Float,                 // 0.0 - 1.0
    val issues: List<String> = emptyList(),
    val recommendation: String? = null,    // e.g. "Review agent output", "All clear"
    val evidence_count: Int = 0,
    val mode: String = "auto",             // LocalLLM, FILEbased, auto, blink
    val flagged_as: String? = null         // hallucinating, looping, inactive, none
)

// ── Activity Event ──

@Serializable
data class ActivityEvent(
    val timestamp: String,                 // ISO 8601
    val event_type: String,                // FILE_READ, FILE_WRITE, SHELL_CMD, COMPILER, TEST, GIT, COMMIT, API, AGENT, USER_INPUT, VERIFICATION
    val description: String,
    val detail: String? = null,
    val agent_id: String? = null
)

// ── Gateway Chat Message ──

@Serializable
data class ChatMessage(
    val message_id: String,
    val text: String,
    val from_user: Boolean,
    val timestamp: String,
    val command: String? = null
)

@Serializable
data class ChatResponse(
    val message_id: String,
    val text: String,
    val agent_id: String? = null,
    val timestamp: String,
    val is_error: Boolean = false
)

// ── Server Status (from stats-server /status endpoint) ──

@Serializable
data class ServerStatus(
    val device: String = "",
    val timestamp: String = "",
    val agents: Int = 0,
    val subagents: Int = 0,
    val tokens_used: Long = 0,
    val tokens_limit: Long = 0,
    val tokens_today: Long = 0,
    val tokens_last_hour: Long = 0,
    val cpu_usage: String = "0%",
    val memory_usage: String = "0%",
    val disk_usage: String = "0%",
    val server_uptime: String = "",
    val overall_status: String = "green",
    val active_projects: Map<String, Int>? = null
)

// ── Connection Config ──

data class GatewayConnection(
    val host: String = "localhost",
    val port: Int = 8080,
    val use_tls: Boolean = false,
    val api_path: String = "/api",
    val stats_path: String = "/stats",
    val auto_reconnect: Boolean = true,
    val reconnect_delay_ms: Long = 5000
)
