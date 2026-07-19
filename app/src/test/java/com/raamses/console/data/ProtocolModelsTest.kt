package com.raamses.console.data

import com.raamses.console.data.models.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProtocolModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Register serialization ──

    @Test
    fun `register message serializes with full capabilities`() {
        val register = RegisterMessage(
            device_id = "test-device-001",
            schema_version = "1.0",
            device_type = "android_console",
            device_name = "Test Console",
            firmware_version = "1.1.0",
            capabilities = Capabilities(
                screen = ScreenCapability(1080, 2400, 32, "oled", 400),
                input = InputCapability(has_touch = true),
                output = OutputCapability(has_vibration = true, has_led = false),
                power = PowerCapability(has_battery = true, battery_level = 85)
            )
        )

        val serialized = json.encodeToString(RegisterMessage.serializer(), register)
        val deserialized = json.decodeFromString(RegisterMessage.serializer(), serialized)

        assertEquals("test-device-001", deserialized.device_id)
        assertEquals("android_console", deserialized.device_type)
        assertEquals("Test Console", deserialized.device_name)
        assertEquals(1080, deserialized.capabilities?.screen?.width)
        assertEquals(2400, deserialized.capabilities?.screen?.height)
        assertEquals("oled", deserialized.capabilities?.screen?.refresh_type)
        assertEquals(true, deserialized.capabilities?.input?.has_touch)
        assertEquals(true, deserialized.capabilities?.output?.has_vibration)
        assertEquals(85, deserialized.capabilities?.power?.battery_level)
    }

    @Test
    fun `register message handles null capabilities`() {
        val register = RegisterMessage(
            device_id = "minimal-device",
            device_type = "legacy"
        )

        val serialized = json.encodeToString(RegisterMessage.serializer(), register)
        val deserialized = json.decodeFromString(RegisterMessage.serializer(), serialized)

        assertEquals("minimal-device", deserialized.device_id)
        assertNull(deserialized.capabilities)
        assertNull(deserialized.device_name)
    }

    // ── RegisterAck ──

    @Test
    fun `register ack includes assigned profile`() {
        val ack = RegisterAck(
            accepted = true,
            server_time = "2026-07-18T20:00:00Z",
            schema_version = "1.0",
            assigned_profile = DeviceProfile(
                name = "RAAMSES Pro",
                display_type = "OLED",
                tier = "pro",
                max_agents = 10,
                features = listOf("verification", "unlimited_devices")
            ),
            heartbeat_interval_ms = 8000
        )

        val serialized = json.encodeToString(RegisterAck.serializer(), ack)
        val deserialized = json.decodeFromString(RegisterAck.serializer(), serialized)

        assertTrue(deserialized.accepted)
        assertEquals("pro", deserialized.assigned_profile?.tier)
        assertEquals(10, deserialized.assigned_profile?.max_agents)
        assertEquals(8000, deserialized.heartbeat_interval_ms)
    }

    @Test
    fun `register ack handles rejection`() {
        val ack = RegisterAck(
            accepted = false,
            server_time = "2026-07-18T20:00:00Z",
            error_message = "Free tier limit reached"
        )

        val serialized = json.encodeToString(RegisterAck.serializer(), ack)
        val deserialized = json.decodeFromString(RegisterAck.serializer(), serialized)

        assertFalse(deserialized.accepted)
        assertEquals("Free tier limit reached", deserialized.error_message)
        assertNull(deserialized.assigned_profile)
    }

    // ── Alert with Short/Medium/Long ──

    @Test
    fun `alert supports short medium and long text`() {
        val alert = Alert(
            severity = "critical",
            title = "Short title",
            message = "Medium message for CYD display",
            long_text = "This is the full long-form text that would appear on a desktop or tablet display. It contains all the details.",
            requires_ack = true,
            vibrate = true,
            category = "verification"
        )

        val serialized = json.encodeToString(Alert.serializer(), alert)
        val deserialized = json.decodeFromString(Alert.serializer(), serialized)

        assertEquals("critical", deserialized.severity)
        assertEquals("Short title", deserialized.title)
        assertEquals("Medium message for CYD display", deserialized.message)
        assertTrue(deserialized.long_text!!.contains("full long-form"))
        assertTrue(deserialized.requires_ack!!)
        assertEquals("verification", deserialized.category)
    }

    // ── Envelope ──

    @Test
    fun `envelope wraps any payload type`() {
        val payload = Alert(severity = "info", title = "Test", message = "Body")
        val envelope = RaamsesEnvelope(
            header = RaamsesHeader(
                message_id = "msg-001",
                timestamp = "2026-07-18T20:00:00Z",
                device_id = "dev-001"
            ),
            payload = json.encodeToString(Alert.serializer(), payload),
            content_type = "application/json"
        )

        val serialized = json.encodeToString(RaamsesEnvelope.serializer(), envelope)
        val deserialized = json.decodeFromString(RaamsesEnvelope.serializer(), serialized)

        assertEquals("msg-001", deserialized.header.message_id)
        assertEquals("application/json", deserialized.content_type)
        assertTrue(deserialized.payload.contains("Test"))
    }

    // ── VerificationResult ──

    @Test
    fun `verification result captures flagged states`() {
        val ver = VerificationResult(
            verified = false,
            confidence = 0.31f,
            issues = listOf("No tests pass", "3x output rate"),
            recommendation = "Halt agent",
            evidence_count = 3,
            mode = "LocalLLM",
            flagged_as = "hallucinating"
        )

        val serialized = json.encodeToString(VerificationResult.serializer(), ver)
        val deserialized = json.decodeFromString(VerificationResult.serializer(), serialized)

        assertFalse(deserialized.verified)
        assertEquals(0.31f, deserialized.confidence)
        assertEquals(2, deserialized.issues.size)
        assertEquals("hallucinating", deserialized.flagged_as)
        assertEquals("LocalLLM", deserialized.mode)
    }

    // ── AgentUpdate ──

    @Test
    fun `agent update includes verification data`() {
        val update = AgentUpdate(
            agent_id = "agent-01",
            agent_name = "Test Agent",
            status = "HALLUCINATING",
            objective = "Write tests",
            current_operation = "Generating boilerplate rapidly",
            home_directory = "/home/user/project",
            token_usage = TokenUsage(total = 50000, last_hour = 10000, today = 25000),
            verified_completion = 0.3f,
            reported_completion = 0.9f,
            verification = VerificationResult(
                verified = false,
                confidence = 0.25f,
                issues = listOf("Suspicious output rate"),
                flagged_as = "hallucinating"
            )
        )

        val serialized = json.encodeToString(AgentUpdate.serializer(), update)
        val deserialized = json.decodeFromString(AgentUpdate.serializer(), serialized)

        assertEquals("HALLUCINATING", deserialized.status)
        assertEquals(0.3f, deserialized.verified_completion)
        assertEquals(0.9f, deserialized.reported_completion)
        assertEquals("hallucinating", deserialized.verification?.flagged_as)
        assertEquals("/home/user/project", deserialized.home_directory)
    }

    // ── VerifierMode enum ──

    @Test
    fun `verifier modes have correct labels`() {
        assertEquals("LocalLLM", VerifierMode.LOCAL_LLM.label)
        assertEquals("FILEbased", VerifierMode.FILE_BASED.label)
        assertEquals("auto", VerifierMode.AUTO.label)
        assertEquals("blink", VerifierMode.BLINK.label)
        assertEquals(4, VerifierMode.entries.size)
    }

    // ── ServerStatus parsing ──

    @Test
    fun `server status parses percentage strings`() {
        val status = ServerStatus(
            cpu_usage = "61%",
            memory_usage = "43%",
            disk_usage = "28%",
            server_uptime = "3d 4h",
            agents = 4,
            overall_status = "yellow"
        )

        val cpu = status.cpu_usage.removeSuffix("%").toFloatOrNull()?.div(100) ?: 0f
        assertEquals(0.61f, cpu)

        val serialized = json.encodeToString(ServerStatus.serializer(), status)
        val deserialized = json.decodeFromString(ServerStatus.serializer(), serialized)

        assertEquals("61%", deserialized.cpu_usage)
        assertEquals("yellow", deserialized.overall_status)
    }

    // ── Edge cases ──

    @Test
    fun `token usage handles large values`() {
        val tokens = TokenUsage(total = 9_999_999, last_hour = 500_000, today = 2_500_000)
        val serialized = json.encodeToString(TokenUsage.serializer(), tokens)
        val deserialized = json.decodeFromString(TokenUsage.serializer(), serialized)

        assertEquals(9_999_999, deserialized.total)
        assertEquals(500_000, deserialized.last_hour)
    }

    @Test
    fun `activity event types have unique symbols`() {
        val symbols = ActivityType.entries.map { it.symbol }
        assertEquals(symbols.size, symbols.distinct().size) // All symbols unique
    }
}
