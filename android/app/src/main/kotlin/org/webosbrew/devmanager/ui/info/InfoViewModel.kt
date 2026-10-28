package org.webosbrew.devmanager.ui.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.model.DeviceInfo
import org.webosbrew.devmanager.data.model.DevModeStatus
import org.webosbrew.devmanager.data.model.PackageInfo
import org.webosbrew.devmanager.data.model.StorageInfo
import org.webosbrew.devmanager.data.repository.DeviceRepository
import org.webosbrew.devmanager.service.ssh.DevModeService
import org.webosbrew.devmanager.service.ssh.LunaService

data class InfoUiState(
    val currentDevice: Device? = null,
    val devices: List<Device> = emptyList(),
    val deviceInfo: DeviceInfo? = null,
    val devModeStatus: DevModeStatus? = null,
    val homebrewAppInfo: PackageInfo? = null,
    val isHomebrewRooted: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val messageSnackbar: String? = null
)

class InfoViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceRepository.getInstance(application)
    private val lunaService = LunaService.getInstance()
    private val devModeService = DevModeService.getInstance()

    private val _uiState = MutableStateFlow(InfoUiState())
    val uiState: StateFlow<InfoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepo.devices.collectLatest { devs ->
                _uiState.value = _uiState.value.copy(devices = devs)
            }
        }

        viewModelScope.launch {
            deviceRepo.selectedDevice.collectLatest { dev ->
                _uiState.value = _uiState.value.copy(currentDevice = dev)
                if (dev != null) {
                    loadInfo()
                } else {
                    _uiState.value = _uiState.value.copy(
                        deviceInfo = null,
                        devModeStatus = null,
                        homebrewAppInfo = null,
                        storageInfo = null,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(messageSnackbar = null)
    }

    fun selectDevice(device: Device) {
        deviceRepo.selectDevice(device)
    }

    fun loadInfo() {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val devInfoRes = lunaService.getDeviceInfo(dev)
            val devModeRes = devModeService.getStatus(dev)
            val storageRes = lunaService.getStorageInfo(dev)
            val appsRes = lunaService.listApps(dev)
            val hbConfigRes = lunaService.getHomebrewChannelConfig(dev)

            val hbApp = appsRes.getOrNull()?.firstOrNull { it.id == "org.webosbrew.hbchannel" }
            val isHbRooted = hbConfigRes.getOrDefault(false)

            if (devInfoRes.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    deviceInfo = devInfoRes.getOrNull(),
                    devModeStatus = devModeRes.getOrNull(),
                    homebrewAppInfo = hbApp,
                    isHomebrewRooted = isHbRooted,
                    storageInfo = storageRes.getOrNull(),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = devInfoRes.exceptionOrNull()?.message ?: "Failed to get device info"
                )
            }
        }
    }

    fun renewDevModeSession() {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = devModeService.renewSession(dev)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Dev Mode session renewed!")
                loadInfo()
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Failed to renew: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
