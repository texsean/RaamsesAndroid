package com.raamses.console.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raamses.console.data.models.*
import com.raamses.console.ui.components.*
import com.raamses.console.ui.theme.*

@Composable
fun DashboardScreen(
    agents: List<AgentStatus>,
    serverHealth: ServerHealth,
    alerts: List<ConsoleAlert>,
    connectionState: ConnectionState,
    onAgentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val flaggedAgents = agents.filter { it.verification?.flaggedAs != null }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // ── Status Bar (htop header) ──
        item {
            HtopStatusBar(serverHealth, flaggedAgents.size, connectionState)
        }

        // ── Connection bar ──
        item {
            ConnectionMiniBar(connectionState)
        }

        // ── Flagged agents alerts ──
        if (flaggedAgents.isNotEmpty()) {
            item {
                FlaggedAgentsBar(flaggedAgents)
            }
        }

        // ── Verification alerts from gateway ──
        val verificationAlerts = alerts.filter { it.category == "verification" }
        if (verificationAlerts.isNotEmpty()) {
            item {
                Text(
                    "VERIFICATION ALERTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeverityWarning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(verificationAlerts, key = { it.id }) { alert ->
                AlertCard(alert = alert, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
        }

        // ── Agent list header ──
        item {
            HtopColumnHeaders()
        }

        // ── Agent rows ──
        items(agents, key = { it.agentId }) { agent ->
            AgentRow(agent = agent, onClick = { onAgentClick(agent.agentId) })
            HorizontalDivider(color = Border, thickness = 0.5.dp)
        }

        // ── Recent events ──
        item {
            Text(
                "RECENT EVENTS",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        val allEvents = agents.flatMap { it.recentActivity }.sortedByDescending { it.timestampSec }.take(10)
        items(allEvents) { event ->
            ActivityFeedItem(event = event, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun HtopStatusBar(
    health: ServerHealth,
    flaggedCount: Int,
    connection: ConnectionState
) {
    Surface(color = Surface, shadowElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Top row: title + overall status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RAAMSES CONSOLE", style = MaterialTheme.typography.titleMedium, color = AccentBlue, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = health.overallStatus.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (health.overallStatus) {
                            "green" -> StatusActive
                            "yellow" -> SeverityWarning
                            else -> SeverityCritical
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Metrics bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricBadge("CPU", "${(health.cpuPercent * 100).toInt()}%", barColor(health.cpuPercent))
                MetricBadge("MEM", "${(health.memoryPercent * 100).toInt()}%", barColor(health.memoryPercent))
                MetricBadge("DISK", "${(health.diskPercent * 100).toInt()}%", barColor(health.diskPercent))
                MetricBadge("UP", health.uptimeDisplay, TextSecondary)
            }

            Spacer(Modifier.height(6.dp))

            // Agent counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CountBadge("AGENTS", health.agentCount, TextPrimary)
                CountBadge("ACTIVE", health.activeAgentCount, StatusActive)
                CountBadge("BLOCKED", health.blockedAgentCount, StatusBlocked)
                CountBadge("FLAGGED", flaggedCount, if (flaggedCount > 0) SeverityCritical else TextMuted)
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun CountBadge(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun ConnectionMiniBar(state: ConnectionState) {
    if (!state.connected) {
        Surface(color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Text(
                "DISCONNECTED — using mock data  |  /connect <host:port> to connect",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    } else {
        Surface(color = StatusActive.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CONNECTED: ${state.host}:${state.port}", style = MaterialTheme.typography.bodySmall, color = StatusActive)
                Text("${state.tier.uppercase()} TIER", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FlaggedAgentsBar(flagged: List<AgentStatus>) {
    Surface(color = SeverityCritical.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "⚠ ${flagged.size} AGENT${if (flagged.size > 1) "S" else ""} FLAGGED",
                style = MaterialTheme.typography.labelMedium,
                color = SeverityCritical,
                fontWeight = FontWeight.Bold
            )
            flagged.forEach { agent ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (agent.verification?.flaggedAs) {
                            "hallucinating" -> "💀"
                            "looping" -> "🔁"
                            else -> "⚠"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${agent.name}: ${agent.verification?.recommendation ?: "Review required"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun HtopColumnHeaders() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("STATUS", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(72.dp))
        Text("AGENT", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
        Text("HOME", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(80.dp))
        Text("PROG", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(60.dp))
        Text("TOKENS", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(50.dp))
    }
}

private fun barColor(fraction: Float): androidx.compose.ui.graphics.Color = when {
    fraction > 0.85f -> SeverityCritical
    fraction > 0.7f -> SeverityWarning
    else -> StatusActive
}
