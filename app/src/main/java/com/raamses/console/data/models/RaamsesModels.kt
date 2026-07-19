package com.raamses.console.data.models

/**
 * Domain-level models for the RAAMSES Console UI.
 * Enrich wire protocol models with UI-specific state.
 */

data class AgentStatus(
    val agentId: String,
    val name: String,
    val status: String,
    val objective: String? = null,
    val currentOperation: String? = null,
    val homeDirectory: String? = null,
    val lastVerifiedWorkSecAgo: Long? = null,
    val lastVerifiedDescription: String? = null,
    val tokenUsage: TokenUsageData? = null,
    val subAgentCount: Int = 0,
    val needsHumanInput: Boolean = false,
    val verifiedCompletion: Float = 0f,
    val reportedCompletion: Float = 0f,
    val verification: VerificationInfo? = null,
    val recentActivity: List<ActivityEvent> = emptyList(),
    val workPulse: List<Int> = emptyList()
)

data class TokenUsageData(
    val total: Long = 0,
    val lastHour: Long = 0,
    val today: Long = 0,
    val limit: Long = 0
)

data class VerificationInfo(
    val verified: Boolean,
    val confidence: Float,
    val issues: List<String> = emptyList(),
    val recommendation: String? = null,
    val evidenceCount: Int = 0,
    val mode: VerifierMode = VerifierMode.AUTO,
    val flaggedAs: String? = null
)

data class ChecklistItem(
    val label: String,
    val status: ItemStatus = ItemStatus.PENDING,
    val evidence: String? = null
)

enum class ItemStatus { DONE, IN_PROGRESS, PENDING, FAILED }

data class ActivityEvent(
    val timestampSec: Long,
    val type: ActivityType,
    val description: String,
    val detail: String? = null,
    val agentId: String? = null
)

enum class ActivityType(val label: String, val symbol: String) {
    FILE_READ("READ", "R"), FILE_WRITE("WRITE", "W"),
    SHELL_CMD("EXEC", ">"), COMPILER("BUILD", "B"),
    TEST("TEST", "T"), GIT("GIT", "G"),
    COMMIT("COMMIT", "C"), API_CALL("API", "A"),
    AGENT("AGENT", "@"), USER_INPUT("INPUT", "?"),
    VERIFICATION("VERIFY", "V")
}

data class ServerHealth(
    val cpuPercent: Float,
    val memoryPercent: Float,
    val diskPercent: Float,
    val uptimeDisplay: String,
    val agentCount: Int,
    val activeAgentCount: Int,
    val blockedAgentCount: Int,
    val flaggedAgentCount: Int = 0,
    val overallStatus: String = "green"
)

data class ConsoleAlert(
    val id: String,
    val severity: String,
    val title: String,
    val message: String,
    val longText: String? = null,
    val timestampSec: Long,
    val requiresAck: Boolean = false,
    val acknowledged: Boolean = false,
    val category: String? = null
)

data class GatewayMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestampSec: Long,
    val command: String? = null,
    val isError: Boolean = false,
    val agentId: String? = null
)

data class ConnectionState(
    val connected: Boolean = false,
    val host: String = "",
    val port: Int = 8080,
    val serverDevice: String = "",
    val serverVersion: String = "",
    val tier: String = "free",
    val maxAgents: Int = 4,
    val lastHeartbeatSecAgo: Long? = null
)
