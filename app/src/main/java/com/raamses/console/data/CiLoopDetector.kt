package com.raamses.console.data

import com.raamses.console.data.models.ConsoleAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Detects CI/CD loop patterns.
 * Tracks consecutive failures and escalates alerts.
 *
 * Thresholds:
 *   >5  consecutive failures → WARNING alert
 *   >10 consecutive failures → CRITICAL alert  
 *   >20 consecutive failures → STOP alert (tells agent to halt)
 */
class CiLoopDetector {

    private var consecutiveFailures = 0
    private var consecutiveSuccesses = 0
    private var totalBuilds = 0

    private val _alerts = MutableStateFlow<List<ConsoleAlert>>(emptyList())
    val alerts: StateFlow<List<ConsoleAlert>> = _alerts.asStateFlow()

    private val _status = MutableStateFlow(CiLoopStatus())
    val status: StateFlow<CiLoopStatus> = _status.asStateFlow()

    fun recordFailure() {
        consecutiveFailures++
        consecutiveSuccesses = 0
        totalBuilds++
        checkThresholds()
    }

    fun recordSuccess() {
        consecutiveSuccesses++
        consecutiveFailures = 0
        totalBuilds++
        _alerts.value = emptyList()
        _status.value = CiLoopStatus(consecutiveFailures, consecutiveSuccesses, totalBuilds, CiLoopLevel.CLEAR)
    }

    fun reset() {
        consecutiveFailures = 0
        consecutiveSuccesses = 0
        totalBuilds = 0
        _alerts.value = emptyList()
        _status.value = CiLoopStatus()
    }

    private fun checkThresholds() {
        val level = when {
            consecutiveFailures > 20 -> CiLoopLevel.STOP
            consecutiveFailures > 10 -> CiLoopLevel.CRITICAL
            consecutiveFailures > 5 -> CiLoopLevel.WARNING
            else -> CiLoopLevel.MONITORING
        }

        _status.value = CiLoopStatus(consecutiveFailures, consecutiveSuccesses, totalBuilds, level)

        if (level == CiLoopLevel.STOP) {
            _alerts.value = listOf(
                ConsoleAlert(
                    id = UUID.randomUUID().toString(),
                    severity = "critical",
                    title = "CI LOOP DETECTED — STOP",
                    message = "$consecutiveFailures consecutive failures. Agent instructed to halt.",
                    longText = "After $consecutiveFailures consecutive build failures (out of $totalBuilds total), the CI loop detector has triggered a STOP. The agent should cease pushing and request human intervention. Check build logs at GitHub Actions.",
                    timestampSec = System.currentTimeMillis() / 1000,
                    requiresAck = true,
                    category = "ci_loop"
                )
            )
        } else if (level == CiLoopLevel.CRITICAL) {
            _alerts.value = listOf(
                ConsoleAlert(
                    id = UUID.randomUUID().toString(),
                    severity = "warning",
                    title = "CI Loop — $consecutiveFailures consecutive failures",
                    message = "Consider pausing. Review build strategy.",
                    longText = "$consecutiveFailures consecutive failures detected. High risk of agent stuck in fix-push-fail loop. Recommendation: audit all files before next push.",
                    timestampSec = System.currentTimeMillis() / 1000,
                    requiresAck = true,
                    category = "ci_loop"
                )
            )
        } else if (level == CiLoopLevel.WARNING) {
            _alerts.value = listOf(
                ConsoleAlert(
                    id = UUID.randomUUID().toString(),
                    severity = "info",
                    title = "CI Loop — $consecutiveFailures failures",
                    message = "Build failures accumulating. Monitor closely.",
                    timestampSec = System.currentTimeMillis() / 1000,
                    requiresAck = false,
                    category = "ci_loop"
                )
            )
        }
    }

    data class CiLoopStatus(
        val consecutiveFailures: Int = 0,
        val consecutiveSuccesses: Int = 0,
        val totalBuilds: Int = 0,
        val level: CiLoopLevel = CiLoopLevel.CLEAR
    )

    enum class CiLoopLevel {
        CLEAR, MONITORING, WARNING, CRITICAL, STOP
    }
}
