package org.webosbrew.devmanager.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.repository.DeviceRepository
import org.webosbrew.devmanager.service.ssh.ShellSession
import org.webosbrew.devmanager.service.ssh.SshConnectionManager

data class TerminalUiState(
    val currentDevice: Device? = null,
    val devices: List<Device> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val output: String = "",
    val errorMessage: String? = null
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceRepository.getInstance(application)
    private val sshManager = SshConnectionManager.getInstance()

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var activeSession: ShellSession? = null

    init {
        viewModelScope.launch {
            deviceRepo.devices.collectLatest { devs ->
                _uiState.value = _uiState.value.copy(devices = devs)
            }
        }

        viewModelScope.launch {
            deviceRepo.selectedDevice.collectLatest { dev ->
                _uiState.value = _uiState.value.copy(currentDevice = dev)
            }
        }
    }

    fun selectDevice(device: Device) {
        deviceRepo.selectDevice(device)
        disconnect()
    }

    fun connect() {
        val dev = _uiState.value.currentDevice ?: return
        if (_uiState.value.isConnecting || _uiState.value.isConnected) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                errorMessage = null,
                output = "Connecting to ${dev.name} (${dev.username}@${dev.host}:${dev.port})...\n"
            )

            try {
                val session = sshManager.openShell(dev) { text ->
                    _uiState.value = _uiState.value.copy(
                        output = _uiState.value.output + text
                    )
                }
                activeSession = session
                _uiState.value = _uiState.value.copy(
                    isConnected = true,
                    isConnecting = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    errorMessage = e.message ?: "Failed to connect",
                    output = _uiState.value.output + "\nConnection error: ${e.message}\n"
                )
            }
        }
    }

    fun sendCommand(cmd: String) {
        activeSession?.sendInput(cmd + "\n")
    }

    fun sendCtrlC() {
        activeSession?.sendBytes(byteArrayOf(3)) // ASCII ETX / Ctrl+C
    }

    fun sendTab() {
        activeSession?.sendBytes(byteArrayOf(9))
    }

    fun sendEsc() {
        activeSession?.sendBytes(byteArrayOf(27))
    }

    fun sendUp() {
        activeSession?.sendBytes(byteArrayOf(27, 91, 65)) // ESC [ A
    }

    fun sendDown() {
        activeSession?.sendBytes(byteArrayOf(27, 91, 66)) // ESC [ B
    }

    fun clearScreen() {
        _uiState.value = _uiState.value.copy(output = "")
    }

    fun disconnect() {
        activeSession?.close()
        activeSession = null
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            isConnecting = false,
            output = _uiState.value.output + "\n[Session disconnected]\n"
        )
    }

    override fun onCleared() {
        super.onCleared()
        activeSession?.close()
        activeSession = null
    }
}
