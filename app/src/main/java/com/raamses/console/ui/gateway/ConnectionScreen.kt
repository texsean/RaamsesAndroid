package com.raamses.console.ui.gateway

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raamses.console.data.models.GatewayConnection
import com.raamses.console.ui.theme.*

@Composable
fun ConnectionScreen(
    currentConfig: GatewayConnection,
    isConnected: Boolean,
    onConnect: (GatewayConnection) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var host by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(currentConfig.port.toString()) }
    var useTls by remember { mutableStateOf(currentConfig.use_tls) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("GATEWAY CONNECTION", style = MaterialTheme.typography.headlineMedium, color = AccentBlue, fontWeight = FontWeight.Bold)

        // Host
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("localhost or 192.168.1.x") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Border
            )
        )

        // Port
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            placeholder = { Text("8765") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Border
            )
        )

        // TLS toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use TLS", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Switch(
                checked = useTls,
                onCheckedChange = { useTls = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }

        // Presets
        Text("QUICK CONNECT", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Pi Gateway" to ("192.168.6.230" to 8765),
                "Stats Server" to ("localhost" to 8765),
                "Python Gateway" to ("localhost" to 42000),
                "C# Server" to ("localhost" to 5000)
            ).forEach { (name, cfg) ->
                FilterChip(
                    selected = host == cfg.first && port == cfg.second.toString(),
                    onClick = { host = cfg.first; port = cfg.second.toString() },
                    label = { Text(name, style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Actions
        if (isConnected) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SeverityCritical)
            ) {
                Text("DISCONNECT", fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = {
                val config = GatewayConnection(
                    host = host.ifBlank { "localhost" },
                    port = port.toIntOrNull() ?: 8765,
                    use_tls = useTls
                )
                onConnect(config)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text(if (isConnected) "RECONNECT" else "CONNECT", fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("CANCEL", color = TextMuted)
        }
    }
}
