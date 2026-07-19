package com.raamses.console.ui.gateway

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raamses.console.data.models.*
import com.raamses.console.ui.theme.*

@Composable
fun GatewayScreen(
    messages: List<GatewayMessage>,
    onSendCommand: (String) -> Unit,
    connectionState: ConnectionState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showSlashMenu by remember { mutableStateOf(false) }
    var slashFilter by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(inputText) {
        showSlashMenu = inputText.startsWith("/") && !inputText.contains(" ")
        slashFilter = if (showSlashMenu) inputText.removePrefix("/").lowercase() else ""
    }

    Box(modifier = modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Connection bar
            ConnectionBar(connectionState, onConnectClick, onDisconnectClick)

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item { WelcomeMessage() }
                }
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
            }

            // Slash overlay
            if (showSlashMenu && slashFilter.isNotEmpty() && inputText.length > 1) {
                SlashCommandOverlay(
                    filter = slashFilter,
                    onSelect = { cmd ->
                        inputText = cmd.command + " "
                        showSlashMenu = false
                    },
                    onDismiss = { showSlashMenu = false }
                )
            }

            // Input bar — also handles raw chat (no slash) via /tell passthrough
            CommandInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        // If no slash prefix, treat as chat pass-through
                        val text = if (!inputText.startsWith("/")) "/tell all $inputText" else inputText.trim()
                        onSendCommand(text)
                        inputText = ""
                        showSlashMenu = false
                    }
                },
                focusRequester = focusRequester,
                isConnected = connectionState.connected
            )
        }
    }
}

@Composable
private fun ConnectionBar(state: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (state.connected) StatusActive else StatusBlocked)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.connected) "CONNECTED" else "DISCONNECTED",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.connected) StatusActive else StatusBlocked
            )
            if (state.connected) {
                Spacer(Modifier.width(8.dp))
                Text("${state.host}:${state.port}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.width(8.dp))
                Text(state.tier.uppercase(), style = MaterialTheme.typography.bodySmall, color = AccentBlue)
            }
        }
        if (state.connected) {
            TextButton(onClick = onDisconnect) {
                Text("DISCONNECT", color = SeverityWarning, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            TextButton(onClick = onConnect) {
                Text("CONNECT", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun WelcomeMessage() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text("RAAMSES GATEWAY", style = MaterialTheme.typography.displayLarge, color = AccentBlue)
        Spacer(Modifier.height(8.dp))
        Text("Agent Command & Control", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        Text("Type / for commands, or just type to chat with agents", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("/agents", "/status", "/verify all", "/help").forEach { cmd ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(cmd, style = MaterialTheme.typography.bodySmall, color = AccentBlue) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceVariant),
                    border = null
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: GatewayMessage) {
    val alignment = if (msg.isFromUser) Alignment.End else Alignment.Start
    val bgColor = if (msg.isFromUser) (if (msg.isError) SeverityCritical else AccentBlue).copy(alpha = 0.15f)
    else SurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(12.dp)
        ) {
            Column {
                if (msg.command != null) {
                    Text(msg.command, style = MaterialTheme.typography.labelMedium, color = SlashHighlight, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.isError) AccentRed else TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTimestamp(msg.timestampSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun CommandInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    isConnected: Boolean
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = CommandBarBackground, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (text.startsWith("/")) "/" else ">",
                style = MaterialTheme.typography.titleLarge,
                color = if (text.startsWith("/")) SlashHighlight else AccentGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = MonoFont),
                cursorBrush = SolidColor(CommandCursor),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("command or chat...", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    innerTextField()
                }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = text.isNotBlank(), modifier = Modifier.size(36.dp)) {
                Text(
                    ">", style = MaterialTheme.typography.titleLarge,
                    color = if (text.isNotBlank()) CommandCursor else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatTimestamp(epoch: Long): String {
    val diff = (System.currentTimeMillis() / 1000) - epoch
    return when {
        diff < 60 -> "now"
        diff < 3600 -> "${diff / 60}m ago"
        else -> "${diff / 3600}h ago"
    }
}
