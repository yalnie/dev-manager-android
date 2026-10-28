package org.webosbrew.devmanager.ui.apps

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.model.PackageInfo
import org.webosbrew.devmanager.data.model.RepoPackage
import org.webosbrew.devmanager.data.model.StorageInfo
import org.webosbrew.devmanager.data.repository.DeviceRepository
import org.webosbrew.devmanager.service.repo.HomebrewRepoService
import org.webosbrew.devmanager.service.ssh.AppInstallService
import org.webosbrew.devmanager.service.ssh.LunaService

enum class AppsTab {
    INSTALLED,
    AVAILABLE
}

data class AppsUiState(
    val currentDevice: Device? = null,
    val devices: List<Device> = emptyList(),
    val currentTab: AppsTab = AppsTab.INSTALLED,
    val installedApps: List<PackageInfo> = emptyList(),
    val repoApps: List<RepoPackage> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val installProgress: Pair<Float, String>? = null,
    val messageSnackbar: String? = null
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceRepository.getInstance(application)
    private val lunaService = LunaService.getInstance()
    private val installService = AppInstallService.getInstance()
    private val repoService = HomebrewRepoService.getInstance()

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

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
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        installedApps = emptyList(),
                        storageInfo = null,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun selectTab(tab: AppsTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        if (tab == AppsTab.AVAILABLE && _uiState.value.repoApps.isEmpty()) {
            loadRepoApps()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(messageSnackbar = null)
    }

    fun selectDevice(device: Device) {
        deviceRepo.selectDevice(device)
    }

    fun refresh() {
        loadInstalledApps()
        loadStorageInfo()
        loadRepoApps()
    }

    fun loadInstalledApps() {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = lunaService.listApps(dev)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    installedApps = result.getOrDefault(emptyList()),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to list apps"
                )
            }
        }
    }

    fun loadStorageInfo() {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = lunaService.getStorageInfo(dev)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(storageInfo = res.getOrNull())
            }
        }
    }

    fun loadRepoApps() {
        viewModelScope.launch {
            val res = repoService.getApps()
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(repoApps = res.getOrDefault(emptyList()))
            }
        }
    }

    fun launchApp(appId: String) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = lunaService.launchApp(dev, appId)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Launched $appId")
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Launch failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun closeApp(appId: String) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = lunaService.closeApp(dev, appId)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Closed $appId")
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Close failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun removeApp(appId: String) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installProgress = Pair(0.5f, "Uninstalling $appId...")
            )
            val res = lunaService.removeApp(dev, appId)
            _uiState.value = _uiState.value.copy(installProgress = null)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Uninstalled $appId")
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Uninstall failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun installLocalIpk(uri: Uri) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = installService.installLocalIpk(
                getApplication(),
                dev,
                uri
            ) { prog, status ->
                _uiState.value = _uiState.value.copy(installProgress = Pair(prog, status))
            }

            _uiState.value = _uiState.value.copy(installProgress = null)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "App installed successfully!")
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Install failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun installRepoApp(repoApp: RepoPackage) {
        val dev = _uiState.value.currentDevice ?: return
        val url = repoApp.ipkUrl ?: return
        viewModelScope.launch {
            val res = installService.installFromUrl(
                getApplication(),
                dev,
                url
            ) { prog, status ->
                _uiState.value = _uiState.value.copy(installProgress = Pair(prog, status))
            }

            _uiState.value = _uiState.value.copy(installProgress = null)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "${repoApp.title} installed successfully!")
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Install failed: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
