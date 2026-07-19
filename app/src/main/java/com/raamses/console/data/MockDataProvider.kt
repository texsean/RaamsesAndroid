package com.raamses.console.data

import com.raamses.console.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Mock data with full protocol support + verifier.
 */
class MockDataProvider {

    private val now = System.currentTimeMillis() / 1000

    private val _agents = MutableStateFlow(generateMockAgents())
    val agents: StateFlow<List<AgentStatus>> = _agents.asStateFlow()

    private val _alerts = MutableStateFlow(generateMockAlerts())
    val alerts: StateFlow<List<ConsoleAlert>> = _alerts.asStateFlow()

    private val _serverHealth = MutableStateFlow(generateMockHealth())
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    fun refresh() {
        _agents.value = generateMockAgents()
        _alerts.value = generateMockAlerts()
        _serverHealth.value = generateMockHealth()
    }

    private fun generateMockAgents(): List<AgentStatus> = listOf(
        AgentStatus(
            agentId = "hermes-gateway-01",
            name = "Gateway Server",
            status = "ACTIVE",
            objective = "Implement Debian gateway registration",
            currentOperation = "Debugging TLS handshake — clang++ running",
            homeDirectory = "/home/sean/projects/raamses-gateway",
            lastVerifiedWorkSecAgo = 12,
            lastVerifiedDescription = "gateway.cpp (+412/-96) — cmake exit 0",
            tokenUsage = TokenUsageData(total = 142_300, lastHour = 8_200, today = 47_800, limit = 250_000),
            subAgentCount = 2,
            needsHumanInput = false,
            verifiedCompletion = 0.48f,
            reportedCompletion = 0.85f,
            verification = VerificationInfo(
                verified = true,
                confidence = 0.82f,
                issues = listOf("2 tests failing in gateway_tests.cpp"),
                recommendation = "Review failed tests before continuing",
                evidenceCount = 14,
                mode = VerifierMode.FILE_BASED,
                flaggedAs = null
            ),
            recentActivity = listOf(
                ActivityEvent(now - 23, ActivityType.FILE_READ, "protocol.xsd"),
                ActivityEvent(now - 18, ActivityType.FILE_WRITE, "gateway.cpp", "+412/-96 lines"),
                ActivityEvent(now - 14, ActivityType.COMPILER, "cmake --build", "exit 0"),
                ActivityEvent(now - 5, ActivityType.TEST, "gateway tests", "18/20 pass"),
                ActivityEvent(now - 2, ActivityType.FILE_WRITE, "gateway_tests.cpp", "+34/-2 lines")
            ),
            workPulse = listOf(1,2,3,7,6,5,7,2,1,1,3,6,7,5,3,1,0,1,2,5,7,6,4,3,2,2,4,6,7,5,4,2,1,2,3,5,7,6,4,3,2,3,5,7,7,5,3,2,1,2,4,5,7,6,5,3,2,3,5,7)
        ),
        AgentStatus(
            agentId = "claude-code-01",
            name = "Claude Code",
            status = "HALLUCINATING",
            objective = "Add OAuth2 support to API layer",
            currentOperation = "Writing token refresh logic — suspiciously fast output",
            homeDirectory = "/home/sean/projects/api-auth",
            lastVerifiedWorkSecAgo = 4,
            lastVerifiedDescription = "auth/refresh.rs (+87/-12) — NO TEST OUTPUT",
            tokenUsage = TokenUsageData(total = 89_700, lastHour = 15_100, today = 31_200, limit = 200_000),
            subAgentCount = 1,
            needsHumanInput = false,
            verifiedCompletion = 0.72f,
            reportedCompletion = 0.95f,
            verification = VerificationInfo(
                verified = false,
                confidence = 0.31f,
                issues = listOf(
                    "Claims 95% complete but no tests pass",
                    "File writes detected but no compiler verification",
                    "3x faster output rate than baseline — possible hallucination loop"
                ),
                recommendation = "HALT AGENT — review all output since last verified checkpoint",
                evidenceCount = 3,
                mode = VerifierMode.LOCAL_LLM,
                flaggedAs = "hallucinating"
            ),
            recentActivity = listOf(
                ActivityEvent(now - 15, ActivityType.FILE_READ, "auth/mod.rs"),
                ActivityEvent(now - 8, ActivityType.FILE_WRITE, "auth/refresh.rs", "+87/-12"),
                ActivityEvent(now - 6, ActivityType.FILE_WRITE, "auth/tokens.rs", "+234/0"),
                ActivityEvent(now - 5, ActivityType.FILE_WRITE, "auth/scopes.rs", "+156/0"),
                ActivityEvent(now - 4, ActivityType.FILE_WRITE, "auth/handlers.rs", "+198/0"),
                ActivityEvent(now - 3, ActivityType.VERIFICATION, "FLAGGED: hallucination suspected")
            ),
            workPulse = listOf(0,0,1,3,5,4,4,3,2,2,8,12,15,14,18,22,25,20,18,22,19,17,20,18,15,14,16,15,14,12,10,8,7,7,6,5,5,4,4,3,3,2,2,1,1,0,0,0,1,2,3,4,5,4,3,2,2,3,4,5)
        ),
        AgentStatus(
            agentId = "hermes-pi-agent",
            name = "Pi Build Agent",
            status = "BLOCKED",
            objective = "Cross-compile ESP32 firmware",
            currentOperation = "Awaiting user: select target board variant",
            homeDirectory = "/home/pi/raamses-firmware",
            lastVerifiedWorkSecAgo = 420,
            lastVerifiedDescription = "cmake: BOARD variant not specified",
            tokenUsage = TokenUsageData(total = 12_400, lastHour = 0, today = 3_100, limit = 100_000),
            subAgentCount = 0,
            needsHumanInput = true,
            verifiedCompletion = 0.35f,
            reportedCompletion = 0.35f,
            verification = VerificationInfo(
                verified = true,
                confidence = 0.95f,
                issues = emptyList(),
                recommendation = "Awaiting user input — no issues detected",
                evidenceCount = 7,
                mode = VerifierMode.FILE_BASED,
                flaggedAs = null
            ),
            recentActivity = listOf(
                ActivityEvent(now - 420, ActivityType.SHELL_CMD, "cmake --preset esp32-s3", "BOARD not set"),
                ActivityEvent(now - 425, ActivityType.USER_INPUT, "Awaiting board selection")
            ),
            workPulse = List(60) { 0 }
        ),
        AgentStatus(
            agentId = "grok-research",
            name = "Grok Research",
            status = "LOOPING",
            objective = "Market research: agent monitoring tools",
            currentOperation = "Re-compiling competitor analysis — 4th iteration",
            homeDirectory = "/home/sean/projects/market-research",
            lastVerifiedWorkSecAgo = 45,
            lastVerifiedDescription = "research/competitors.md — SAME CONTENT 4x",
            tokenUsage = TokenUsageData(total = 56_200, lastHour = 22_100, today = 18_500, limit = 150_000),
            subAgentCount = 0,
            needsHumanInput = false,
            verifiedCompletion = 0.65f,
            reportedCompletion = 0.70f,
            verification = VerificationInfo(
                verified = false,
                confidence = 0.45f,
                issues = listOf(
                    "Same file rewritten 4 times with identical content",
                    "No new evidence in last 15 minutes",
                    "Token burn rate 3x normal for this task type"
                ),
                recommendation = "Break the loop — assign a new, specific sub-task",
                evidenceCount = 2,
                mode = VerifierMode.BLINK,
                flaggedAs = "looping"
            ),
            recentActivity = listOf(
                ActivityEvent(now - 180, ActivityType.FILE_WRITE, "research/competitors.md", "+156/-23"),
                ActivityEvent(now - 120, ActivityType.FILE_WRITE, "research/competitors.md", "+158/-21 (SAME)"),
                ActivityEvent(now - 80, ActivityType.FILE_WRITE, "research/competitors.md", "+156/-23 (SAME)"),
                ActivityEvent(now - 45, ActivityType.VERIFICATION, "LOOP DETECTED: 4 rewrites, no delta")
            ),
            workPulse = List(60) { i -> if (i < 5) 1 else 0 }
        )
    )

    private fun generateMockAlerts(): List<ConsoleAlert> = listOf(
        ConsoleAlert(
            id = UUID.randomUUID().toString(),
            severity = "critical",
            title = "AGENT HALLUCINATING",
            message = "Claude Code flagged for hallucination — 95% reported vs 72% verified",
            longText = "Claude Code (claude-code-01) has been flagged by the LocalLLM verifier for potential hallucination. 3 issues detected: claims 95% completion but no tests pass, file writes without compiler verification, 3x faster output than baseline. Recommendation: halt agent and review all output since last verified checkpoint.",
            timestampSec = now - 30,
            requiresAck = true,
            category = "verification"
        ),
        ConsoleAlert(
            id = UUID.randomUUID().toString(),
            severity = "warning",
            title = "LOOP DETECTED",
            message = "Grok Research rewriting same file — 4 iterations, no delta",
            longText = "Grok Research has rewritten research/competitors.md 4 times in the last 15 minutes with no material difference. Token burn rate is 3x normal for this task. Recommendation: break the loop by assigning a new, specific sub-task.",
            timestampSec = now - 45,
            requiresAck = true,
            category = "verification"
        ),
        ConsoleAlert(
            id = UUID.randomUUID().toString(),
            severity = "warning",
            title = "Report Mismatch",
            message = "Gateway Server: 85% reported but only 48% verified",
            longText = "Evidence-backed verification found only 48% of tasks complete despite agent claiming 85%. 2 tests failing in gateway_tests.cpp. No systemd service file exists yet.",
            timestampSec = now - 60,
            requiresAck = false,
            category = "verification"
        ),
        ConsoleAlert(
            id = UUID.randomUUID().toString(),
            severity = "info",
            title = "Agent Blocked",
            message = "Pi Build Agent awaiting board selection for 7 minutes",
            timestampSec = now - 420,
            requiresAck = true,
            category = "agent"
        )
    )

    private fun generateMockHealth(): ServerHealth = ServerHealth(
        cpuPercent = 0.61f,
        memoryPercent = 0.43f,
        diskPercent = 0.28f,
        uptimeDisplay = "3d 4h 12m",
        agentCount = 4,
        activeAgentCount = 1,
        blockedAgentCount = 1,
        flaggedAgentCount = 2,   // Claude hallucinating + Grok looping
        overallStatus = "yellow"
    )
}
