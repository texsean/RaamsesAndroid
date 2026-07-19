package com.raamses.console.data

import com.raamses.console.data.models.*
import com.raamses.console.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class GatewayCommandsTest {

    // ── Status colors ──

    @Test
    fun `statusColor returns correct colors for all states`() {
        assertEquals(com.raamses.console.ui.theme.StatusActive, statusColor("ACTIVE"))
        assertEquals(com.raamses.console.ui.theme.StatusQuiet, statusColor("QUIET"))
        assertEquals(com.raamses.console.ui.theme.StatusIdle, statusColor("IDLE"))
        assertEquals(com.raamses.console.ui.theme.StatusStale, statusColor("STALE"))
        assertEquals(com.raamses.console.ui.theme.StatusBlocked, statusColor("BLOCKED"))
        assertEquals(com.raamses.console.ui.theme.StatusUnverified, statusColor("UNVERIFIED"))
        assertEquals(com.raamses.console.ui.theme.SeverityCritical, statusColor("HALLUCINATING"))
        assertEquals(com.raamses.console.ui.theme.AccentOrange, statusColor("LOOPING"))
    }

    @Test
    fun `statusColor returns muted for unknown states`() {
        assertEquals(com.raamses.console.ui.theme.TextMuted, statusColor("RANDOM_STATE"))
        assertEquals(com.raamses.console.ui.theme.TextMuted, statusColor(""))
    }

    // ── Activity colors ──

    @Test
    fun `activityColor returns correct colors`() {
        assertEquals(com.raamses.console.ui.theme.AccentGreen, activityColor(ActivityType.FILE_WRITE))
        assertEquals(com.raamses.console.ui.theme.AccentBlue, activityColor(ActivityType.TEST))
        assertEquals(com.raamses.console.ui.theme.AccentOrange, activityColor(ActivityType.COMPILER))
        assertEquals(com.raamses.console.ui.theme.StatusBlocked, activityColor(ActivityType.USER_INPUT))
        assertEquals(com.raamses.console.ui.theme.SeverityWarning, activityColor(ActivityType.VERIFICATION))
        assertEquals(com.raamses.console.ui.theme.StatusActive, activityColor(ActivityType.COMMIT))
    }

    // ── Token formatting ──

    @Test
    fun `formatTokenCount formats correctly`() {
        assertEquals("0", formatTokenCount(0))
        assertEquals("500", formatTokenCount(500))
        assertEquals("1.5K", formatTokenCount(1_500))
        assertEquals("99.9K", formatTokenCount(99_900))
        assertEquals("1.0M", formatTokenCount(1_000_000))
        assertEquals("2.5M", formatTokenCount(2_500_000))
    }

    // ── Time formatting ──

    @Test
    fun `formatSecondsAgo returns correct strings`() {
        val now = System.currentTimeMillis() / 1000
        assertEquals("0s", formatSecondsAgo(now))       // just now
        assertEquals("30s", formatSecondsAgo(now - 30))
        assertEquals("5m", formatSecondsAgo(now - 300))
        assertEquals("2h", formatSecondsAgo(now - 7200))
        assertEquals("3d", formatSecondsAgo(now - 259200))
    }

    // ── Verifier mode parsing ──

    @Test
    fun `verifier mode parses from string case-insensitive`() {
        assertEquals(VerifierMode.LOCAL_LLM, parseVerifierMode("LocalLLM"))
        assertEquals(VerifierMode.LOCAL_LLM, parseVerifierMode("localllm"))
        assertEquals(VerifierMode.FILE_BASED, parseVerifierMode("FILEbased"))
        assertEquals(VerifierMode.AUTO, parseVerifierMode("auto"))
        assertEquals(VerifierMode.BLINK, parseVerifierMode("blink"))
    }

    @Test
    fun `unknown verifier mode defaults to AUTO`() {
        assertEquals(VerifierMode.AUTO, parseVerifierMode("unknown"))
        assertEquals(VerifierMode.AUTO, parseVerifierMode(""))
    }

    // ── Mock data integrity ──

    @Test
    fun `mock provider generates exactly 4 agents`() {
        val provider = MockDataProvider()
        assertEquals(4, provider.agents.value.size)
    }

    @Test
    fun `mock provider has at least one flagged agent`() {
        val provider = MockDataProvider()
        val flagged = provider.agents.value.filter { it.verification?.flaggedAs != null }
        assertTrue("Should have at least one flagged agent", flagged.isNotEmpty())
    }

    @Test
    fun `mock provider has at least one hallucinating and one looping agent`() {
        val provider = MockDataProvider()
        val hallucinating = provider.agents.value.filter { it.verification?.flaggedAs == "hallucinating" }
        val looping = provider.agents.value.filter { it.verification?.flaggedAs == "looping" }
        assertEquals(1, hallucinating.size)
        assertEquals(1, looping.size)
    }

    @Test
    fun `mock alerts include verification category`() {
        val provider = MockDataProvider()
        val verAlerts = provider.alerts.value.filter { it.category == "verification" }
        assertTrue("Should have verification alerts", verAlerts.isNotEmpty())
    }

    @Test
    fun `mock server health reflects flagged count`() {
        val provider = MockDataProvider()
        val health = provider.serverHealth.value
        assertEquals(2, health.flaggedAgentCount)
        assertEquals("yellow", health.overallStatus)
    }

    @Test
    fun `refresh generates new data`() {
        val provider = MockDataProvider()
        val before = provider.alerts.value.size
        provider.refresh()
        // After refresh, agents still 4
        assertEquals(4, provider.agents.value.size)
    }

    companion object {
        private fun parseVerifierMode(mode: String): VerifierMode {
            return try {
                VerifierMode.valueOf(mode.uppercase())
            } catch (_: Exception) {
                VerifierMode.AUTO
            }
        }
    }
}
