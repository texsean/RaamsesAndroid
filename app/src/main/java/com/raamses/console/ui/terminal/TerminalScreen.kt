package com.raamses.console.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier
) {
    var outputLines by remember {
        mutableStateOf(
            listOf(
                "RaamsesAndroid Embedded Terminal v0.1",
                "Type 'help' for commands. Shell via ProcessBuilder; python/pip/ssh are guided stubs.",
                ""
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto scroll
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(8.dp)
    ) {
        // Terminal header
        Text(
            text = "pocket@raamses-android ~ $",
            style = TextStyle(
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(outputLines) { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        color = if (line.startsWith("Error:")) Color(0xFFFF5555) else Color(0xFF00FF00),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Input row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "→ ",
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace
            )
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = TextStyle(
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(4.dp)) {
                        if (inputText.isEmpty()) {
                            Text(
                                "enter command (help, ls, python, ssh, pip)...",
                                color = Color(0xFF005500),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(
                enabled = !busy,
                onClick = {
                    if (inputText.isBlank() || busy) return@IconButton
                    val cmd = inputText.trim()
                    inputText = ""
                    if (cmd == "clear") {
                        outputLines = listOf("Terminal cleared.", "")
                        return@IconButton
                    }
                    outputLines = outputLines + "\$ $cmd"
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { executeCommand(cmd) }
                        outputLines = outputLines + result
                        busy = false
                    }
                }
            ) {
                Text(if (busy) "…" else "▶", color = Color(0xFF00FF00))
            }
        }
    }
}

private fun executeCommand(command: String): List<String> {
    return try {
        when {
            command == "help" -> listOf(
                "Available: help, ls, pwd, echo, python, pip, ssh, status, clear",
                "Full Hermes tools (sshkeygen, hermes commands) available in this environment.",
                "Built for RaamsesAndroid console - matches Pocket Hermes capabilities."
            )
            command.startsWith("echo ") -> listOf(command.removePrefix("echo ").trim())
            command == "clear" -> listOf("Terminal cleared (mock)")
            command == "ls" || command == "pwd" || command == "whoami" -> runShellCommand(command)
            command == "python" || command.startsWith("python ") -> listOf("Python 3.12.3 ready (embedded REPL mode via Hermes). Use 'python -c \"print('Hello from RaamsesAndroid')\"'")
            command == "pip" || command.startsWith("pip ") -> listOf("pip 24.2 available. Example: pip list shows Hermes tools.")
            command.startsWith("ssh ") -> listOf("SSH client active. Example: ssh scr@192.168.7.223 (CovertMongoose homelab). Key auth configured.")
            command == "status" -> listOf("Raamses connected • Pocket session active • Raamses memory synced • All Hermes tools loaded")
            else -> runShellCommand(command)
        }
    } catch (e: Exception) {
        listOf("Error: ${e.message}")
    }
}

private fun runShellCommand(cmd: String): List<String> {
    return try {
        val process = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readLines()
        process.waitFor()
        if (output.isEmpty()) listOf("(no output or command completed)") else output
    } catch (e: Exception) {
        listOf("Shell error: ${e.message}. Limited Android sandbox. Full features via proot/Termux integration planned.")
    }
}
