package com.raamses.console.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raamses.console.data.models.*
import com.raamses.console.ui.components.*
import com.raamses.console.ui.theme.*

@Composable
fun AgentDetailScreen(agent: AgentStatus, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back
        item {
            TextButton(onClick = onBack) {
                Text("< BACK", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(agent.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(agent.agentId, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    agent.homeDirectory?.let {
                        Text("🏠 $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                StatusIndicator(agent.status)
            }
        }

        // Verification result (if verifier ran)
        agent.verification?.let { ver ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (ver.flaggedAs) {
                            "hallucinating" -> SeverityCritical.copy(alpha = 0.12f)
                            "looping" -> AccentOrange.copy(alpha = 0.12f)
                            else -> CardBackground
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("VERIFICATION", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            Text(
                                "Mode: ${ver.mode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentBlue
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Confidence bar
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Confidence:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.width(90.dp)
                            )
                            LinearProgressIndicator(
                                progress = { ver.confidence },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = when {
                                    ver.confidence > 0.8f -> StatusActive
                                    ver.confidence > 0.5f -> SeverityWarning
                                    else -> SeverityCritical
                                },
                                trackColor = SurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(ver.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Evidence: ${ver.evidenceCount} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )

                        // Issues
                        if (ver.issues.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("ISSUES:", style = MaterialTheme.typography.labelMedium, color = SeverityWarning)
                            ver.issues.forEach { issue ->
                                Text("  • $issue", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        // Recommendation
                        ver.recommendation?.let {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = AccentBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "→ $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentBlue,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Flagged status
                        ver.flaggedAs?.let { flag ->
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (flag) {
                                        "hallucinating" -> "💀"
                                        "looping" -> "🔁"
                                        else -> "⚠"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "FLAGGED: $flag".uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when (flag) {
                                        "hallucinating" -> SeverityCritical
                                        "looping" -> AccentOrange
                                        else -> SeverityWarning
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Objective + Current Operation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("OBJECTIVE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Text(agent.objective ?: "N/A", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    if (agent.currentOperation != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("CURRENT", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(agent.currentOperation, style = MaterialTheme.typography.bodyLarge, color = AccentBlue)
                    }
                    agent.lastVerifiedWorkSecAgo?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("LAST VERIFIED: ${formatSecondsAgo(it)}", style = MaterialTheme.typography.labelMedium, color = StatusActive)
                        agent.lastVerifiedDescription?.let { d ->
                            Text(d, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Tokens
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOKENS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        agent.tokenUsage?.let { t ->
                            TokenStat("TODAY", t.today, t.limit)
                            TokenStat("LAST HR", t.lastHour, 0)
                            TokenStat("TOTAL", t.total, 0)
                        }
                    }
                }
            }
        }

        // Completion
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("COMPLETION", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            "Verified ${(agent.verifiedCompletion * 100).toInt()}%  |  Reported ${(agent.reportedCompletion * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (agent.reportedCompletion != agent.verifiedCompletion) SeverityWarning else TextSecondary
                        )
                    }
                    if (agent.reportedCompletion != agent.verifiedCompletion) {
                        Spacer(Modifier.height(6.dp))
                        Surface(color = SeverityWarning.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "⚠ MISMATCH: Agent overreports by ${((agent.reportedCompletion - agent.verifiedCompletion) * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeverityWarning,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Activity feed
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ACTIVITY", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    agent.recentActivity.forEach { event ->
                        ActivityFeedItem(event)
                    }
                }
            }
        }

        // Quick commands
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("COMMANDS", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "/approve ${agent.agentId}" to AccentGreen,
                            "/verify ${agent.agentId}" to AccentBlue,
                            "/pause ${agent.agentId}" to AccentOrange,
                            "/stop ${agent.agentId}" to AccentRed
                        ).forEach { (cmd, color) ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(cmd, style = MaterialTheme.typography.bodySmall, color = color) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceVariant),
                                border = null
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TokenStat(label: String, value: Long, limit: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(formatTokenCount(value), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        if (limit > 0) {
            LinearProgressIndicator(
                progress = { (value.toFloat() / limit).coerceIn(0f, 1f) },
                modifier = Modifier.width(60.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (value > limit * 0.8f) SeverityWarning else AccentBlue,
                trackColor = SurfaceVariant
            )
        }
    }
}
