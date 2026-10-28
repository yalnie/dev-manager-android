package org.webosbrew.devmanager.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webosbrew.devmanager.data.model.AuthType
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.repository.DeviceRepository
import org.webosbrew.devmanager.service.ssh.SshConnectionManager

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val selectedDevice: Device? = null,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val messageSnackbar: String? = null
)

class DevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceRepository.getInstance(application)
    private val sshManager = SshConnectionManager.getInstance()

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepo.devices.collectLatest { devs ->
                _uiState.value = _uiState.value.copy(devices = devs)
            }
        }

        viewModelScope.launch {
            deviceRepo.selectedDevice.collectLatest { dev ->
                _uiState.value = _uiState.value.copy(selectedDevice = dev)
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(messageSnackbar = null)
    }

    fun setDefaultDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepo.setDefaultDevice(deviceId)
            _uiState.value = _uiState.value.copy(messageSnackbar = "Default device updated")
        }
    }

    fun selectDevice(device: Device) {
        deviceRepo.selectDevice(device)
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepo.deleteDevice(deviceId)
            _uiState.value = _uiState.value.copy(messageSnackbar = "Device removed")
        }
    }

    fun addDevModeDevice(
        name: String,
        host: String,
        passphrase: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            // 1. Fetch key from 9991
            val keyRes = deviceRepo.fetchDevModeKey(host, passphrase)
            if (keyRes.isFailure) {
                _uiState.value = _uiState.value.copy(isTesting = false)
                onError(keyRes.exceptionOrNull()?.message ?: "Failed to fetch private key from TV")
                return@launch
            }

            val privKey = keyRes.getOrNull() ?: ""

            // 2. Build device object
            val device = Device(
                name = name.ifBlank { "webOS TV" },
                host = host.trim(),
                port = 9922,
                username = "prisoner",
                authType = AuthType.KEY,
                privateKey = privKey,
                passphrase = passphrase.trim()
            )

            // 3. Test SSH connection
            try {
                val cmd = sshManager.executeCommand(device, "echo test")
                if (cmd.exitCode == 0 || cmd.stdout.contains("test")) {
                    deviceRepo.addDevice(device)
                    _uiState.value = _uiState.value.copy(isTesting = false, messageSnackbar = "Device added successfully!")
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(isTesting = false)
                    onError("SSH verification failed on port 9922: ${cmd.stderr}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isTesting = false)
                onError("Failed to connect via SSH: ${e.message}")
            }
        }
    }

    fun addCustomDevice(
        name: String,
        host: String,
        port: Int,
        username: String,
        authType: AuthType,
        password: String?,
        privateKey: String?,
        passphrase: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val device = Device(
                name = name.ifBlank { host },
                host = host.trim(),
                port = port,
                username = username.ifBlank { "root" },
                authType = authType,
                password = password?.takeIf { it.isNotBlank() },
                privateKey = privateKey?.takeIf { it.isNotBlank() },
                passphrase = passphrase?.takeIf { it.isNotBlank() }
            )

            try {
                val cmd = sshManager.executeCommand(device, "uname -a")
                if (cmd.exitCode == 0 || cmd.stdout.isNotBlank()) {
                    deviceRepo.addDevice(device)
                    _uiState.value = _uiState.value.copy(isTesting = false, messageSnackbar = "Device added successfully!")
                    onSuccess()
                } else {
                    _uiState.value = _uiState.value.copy(isTesting = false)
                    onError("SSH verification failed: ${cmd.stderr}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isTesting = false)
                onError("Failed to connect: ${e.message}")
            }
        }
    }
}
