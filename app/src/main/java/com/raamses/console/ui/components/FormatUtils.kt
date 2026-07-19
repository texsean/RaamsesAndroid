package com.raamses.console.ui.components

import com.raamses.console.data.models.ActivityType
import com.raamses.console.data.models.VerifierMode

fun statusColorHex(status: String): Long = when (status) {
    "ACTIVE" -> 0xFF00E676; "QUIET" -> 0xFFFFCA28; "IDLE" -> 0xFF78909C
    "STALE" -> 0xFFFF7043; "BLOCKED" -> 0xFFFF1744; "UNVERIFIED" -> 0xFFAA00FF
    "HALLUCINATING" -> 0xFFFF1744; "LOOPING" -> 0xFFFF9100
    else -> 0xFF616161
}

fun activityColorHex(type: ActivityType): Long = when (type) {
    ActivityType.FILE_WRITE -> 0xFF00E676; ActivityType.TEST -> 0xFF448AFF
    ActivityType.COMPILER -> 0xFFFF9100; ActivityType.USER_INPUT -> 0xFFFF1744
    ActivityType.VERIFICATION -> 0xFFFF9100; ActivityType.COMMIT -> 0xFF00E676
    else -> 0xFF9E9E9E
}

fun formatSecondsAgo(epoch: Long): String {
    val diff = (System.currentTimeMillis() / 1000) - epoch
    return when { diff < 60 -> "${diff}s"; diff < 3600 -> "${diff / 60}m"; diff < 86400 -> "${diff / 3600}h"; else -> "${diff / 86400}d" }
}

fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}

fun parseVerifierMode(mode: String): VerifierMode = try { VerifierMode.valueOf(mode.uppercase()) } catch (_: Exception) { VerifierMode.AUTO }
