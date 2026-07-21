package com.raamses.console.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raamses.console.data.models.NetworkLogEntry
import com.raamses.console.ui.components.FormatUtils.formatSecondsAgo
import com.raamses.console.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogScreen(
    entries: List<NetworkLogEntry>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("NETWORK LOG", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("${entries.size}/200", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No network activity yet.\nConnect to a server to see traffic.", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(entries.reversed()) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: NetworkLogEntry) {
    val isOut = entry.direction == "OUT"
    val bg = if (isOut) AccentBlue.copy(alpha = 0.08f) else AccentGreen.copy(alpha = 0.08f)
    val arrow = if (isOut) "→" else "←"
    val arrowColor = if (isOut) AccentBlue else AccentGreen
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampSec * 1000))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$arrow ",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = arrowColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = TextMuted,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
