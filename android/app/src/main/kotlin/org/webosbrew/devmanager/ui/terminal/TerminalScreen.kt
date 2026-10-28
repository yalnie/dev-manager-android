package org.webosbrew.devmanager.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.webosbrew.devmanager.ui.common.TopDeviceBar

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onNavigateToDevices: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val terminalScrollState = rememberScrollState()

    // Auto-scroll to bottom on new output
    LaunchedEffect(uiState.output) {
        terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopDeviceBar(
                currentDevice = uiState.currentDevice,
                devices = uiState.devices,
                onSelectDevice = { viewModel.selectDevice(it) },
                onAddDeviceClick = onNavigateToDevices,
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = { viewModel.clearScreen() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Disconnect", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.connect() },
                            enabled = uiState.currentDevice != null && !uiState.isConnecting
                        ) {
                            if (uiState.isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Connect", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.currentDevice == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = onNavigateToDevices) {
                        Text("Select or Add Device")
                    }
                }
            } else if (!uiState.isConnected && !uiState.isConnecting && uiState.output.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Start SSH Terminal on ${uiState.currentDevice?.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.connect() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Terminal")
                        }
                    }
                }
            } else {
                // Terminal Display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF101014))
                        .padding(12.dp)
                ) {
                    Text(
                        text = uiState.output,
                        color = Color(0xFFDCE2F9),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScrollState)
                    )
                }

                // Quick Keys Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TerminalChip(text = "Ctrl+C", onClick = { viewModel.sendCtrlC() })
                    Spacer(modifier = Modifier.width(6.dp))
                    TerminalChip(text = "Tab", onClick = { viewModel.sendTab() })
                    Spacer(modifier = Modifier.width(6.dp))
                    TerminalChip(text = "Esc", onClick = { viewModel.sendEsc() })
                    Spacer(modifier = Modifier.width(6.dp))
                    TerminalChip(text = "↑", onClick = { viewModel.sendUp() })
                    Spacer(modifier = Modifier.width(6.dp))
                    TerminalChip(text = "↓", onClick = { viewModel.sendDown() })
                    Spacer(modifier = Modifier.width(6.dp))
                    TerminalChip(text = "Clear", onClick = { viewModel.clearScreen() })
                }

                // Command Input Line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Command...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotEmpty()) {
                                viewModel.sendCommand(inputText)
                                inputText = ""
                            }
                        }),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotEmpty()) {
                                viewModel.sendCommand(inputText)
                                inputText = ""
                            }
                        },
                        enabled = uiState.isConnected && inputText.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalChip(text: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        shape = RoundedCornerShape(6.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
