package com.raamses.console.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raamses.console.data.models.*
import com.raamses.console.ui.theme.*

// ═══════════════════════════════════════════════
// Status Indicator — color-coded agent state
// ═══════════════════════════════════════════════

@Composable
fun StatusIndicator(status: String, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    val pulse = status == "HALLUCINATING" || status == "LOOPING"

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (pulse) {
            val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(Unit) {
                androidx.compose.animation.core.animate(
                    alpha, 0.3f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(500),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha.value))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

fun statusColor(status: String): Color = when (status) {
    "ACTIVE" -> StatusActive
    "QUIET" -> StatusQuiet
    "IDLE" -> StatusIdle
    "STALE" -> StatusStale
    "BLOCKED" -> StatusBlocked
    "UNVERIFIED" -> StatusUnverified
    "HALLUCINATING" -> SeverityCritical
    "LOOPING" -> AccentOrange
    else -> TextMuted
}

// ═══════════════════════════════════════════════
// Verifier Badge — shows verification result
// ═══════════════════════════════════════════════

@Composable
fun VerifierBadge(verification: VerificationInfo?, modifier: Modifier = Modifier) {
    if (verification == null) return

    val (bgColor, text) = when {
        verification.flaggedAs == "hallucinating" -> SeverityCritical.copy(alpha = 0.2f) to "💀 HALLUCINATING"
        verification.flaggedAs == "looping" -> AccentOrange.copy(alpha = 0.2f) to "🔁 LOOPING"
        !verification.verified -> SeverityWarning.copy(alpha = 0.15f) to "⚠ UNVERIFIED"
        verification.confidence > 0.8f -> StatusActive.copy(alpha = 0.15f) to "✓ VERIFIED ${(verification.confidence * 100).toInt()}%"
        else -> StatusQuiet.copy(alpha = 0.15f) to "~ VERIFIED ${(verification.confidence * 100).toInt()}%"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = statusColor(verification.flaggedAs ?: "ACTIVE"))
            if (verification.issues.isNotEmpty()) {
                verification.issues.take(2).forEach { issue ->
                    Text("  • $issue", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            verification.recommendation?.let {
                Text("  → $it", style = MaterialTheme.typography.bodySmall, color = AccentBlue)
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Agent Row (htop-style compact)
// ═══════════════════════════════════════════════

@Composable
fun AgentRow(
    agent: AgentStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (agent.verification?.flaggedAs != null)
            statusColor(agent.verification.flaggedAs!!).copy(alpha = 0.05f)
        else CardBackground,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            StatusIndicator(agent.status, modifier = Modifier.width(72.dp))

            // Agent name + objective
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                agent.objective?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
                }
            }

            // Home dir (truncated)
            agent.homeDirectory?.let {
                Text(
                    text = it.split("/").takeLast(2).joinToString("/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(8.dp))

            // Progress
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(60.dp)) {
                Text(
                    "${(agent.verifiedCompletion * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (agent.verifiedCompletion < agent.reportedCompletion) SeverityWarning else StatusActive
                )
                LinearProgressIndicator(
                    progress = { agent.verifiedCompletion },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (agent.verifiedCompletion < agent.reportedCompletion) SeverityWarning else StatusActive,
                    trackColor = SurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            // Tokens today
            agent.tokenUsage?.let { tokens ->
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(50.dp)) {
                    Text(formatTokenCount(tokens.today), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("tokens", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            // Verifier flag
            if (agent.verification?.flaggedAs != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (agent.verification.flaggedAs) {
                        "hallucinating" -> "💀"
                        "looping" -> "🔁"
                        else -> "⚠"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Activity Feed Item
// ═══════════════════════════════════════════════

@Composable
fun ActivityFeedItem(event: ActivityEvent, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatSecondsAgo(event.timestampSec),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = event.type.symbol,
            style = MaterialTheme.typography.labelMedium,
            color = activityColor(event.type),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = event.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        event.detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
        }
    }
}

fun activityColor(type: ActivityType): Color = when (type) {
    ActivityType.FILE_WRITE -> AccentGreen
    ActivityType.TEST -> AccentBlue
    ActivityType.COMPILER -> AccentOrange
    ActivityType.USER_INPUT -> StatusBlocked
    ActivityType.VERIFICATION -> SeverityWarning
    ActivityType.COMMIT -> StatusActive
    else -> TextSecondary
}

// ═══════════════════════════════════════════════
// Alert Card
// ═══════════════════════════════════════════════

@Composable
fun AlertCard(alert: ConsoleAlert, onAck: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val severityColor = when (alert.severity) {
        "critical" -> SeverityCritical
        "warning" -> SeverityWarning
        else -> SeverityInfo
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = severityColor.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.width(3.dp).height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(severityColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(alert.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(formatSecondsAgo(alert.timestampSec), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Spacer(Modifier.height(4.dp))
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                if (alert.requiresAck && !alert.acknowledged && onAck != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAck) {
                        Text("ACKNOWLEDGE", color = severityColor, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Utility functions are in FormatUtils.kt (no Compose dependency)
