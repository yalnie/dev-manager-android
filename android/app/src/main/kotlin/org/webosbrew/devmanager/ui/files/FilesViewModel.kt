package org.webosbrew.devmanager.ui.files

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
import org.webosbrew.devmanager.data.model.RemoteFile
import org.webosbrew.devmanager.data.model.StorageInfo
import org.webosbrew.devmanager.data.repository.DeviceRepository
import org.webosbrew.devmanager.service.ssh.LunaService
import org.webosbrew.devmanager.service.ssh.RemoteFileService
import java.io.InputStream
import java.io.OutputStream

data class FilesUiState(
    val currentDevice: Device? = null,
    val devices: List<Device> = emptyList(),
    val currentPath: String = "/media/developer",
    val history: List<String> = listOf("/media/developer"),
    val historyIndex: Int = 0,
    val files: List<RemoteFile> = emptyList(),
    val selectedFiles: Set<RemoteFile> = emptySet(),
    val storageInfo: StorageInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val transferProgress: Pair<Float, String>? = null,
    val messageSnackbar: String? = null
) {
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex < history.size - 1

    val breadcrumbParts: List<String>
        get() {
            if (currentPath == "/" || currentPath.isEmpty()) return listOf("/")
            val parts = currentPath.split("/").filter { it.isNotEmpty() }
            return listOf("/") + parts
        }
}

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceRepository.getInstance(application)
    private val fileService = RemoteFileService.getInstance()
    private val lunaService = LunaService.getInstance()

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

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
                    val initialPath = if (dev.username == "root") "/" else "/media/developer"
                    _uiState.value = _uiState.value.copy(
                        currentPath = initialPath,
                        history = listOf(initialPath),
                        historyIndex = 0
                    )
                    loadFiles(initialPath)
                    loadStorageInfo()
                } else {
                    _uiState.value = _uiState.value.copy(
                        files = emptyList(),
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

    fun loadStorageInfo() {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            val res = lunaService.getStorageInfo(dev, _uiState.value.currentPath)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(storageInfo = res.getOrNull())
            }
        }
    }

    fun loadFiles(path: String) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, selectedFiles = emptySet())
            val res = fileService.listFiles(dev, path)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    files = res.getOrDefault(emptyList()),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = res.exceptionOrNull()?.message ?: "Failed to list files"
                )
            }
        }
    }

    fun navigateTo(path: String) {
        val state = _uiState.value
        val newHistory = state.history.subList(0, state.historyIndex + 1) + path
        _uiState.value = state.copy(
            currentPath = path,
            history = newHistory,
            historyIndex = newHistory.size - 1
        )
        loadFiles(path)
    }

    fun navigateBack() {
        val state = _uiState.value
        if (state.canGoBack) {
            val newIdx = state.historyIndex - 1
            val path = state.history[newIdx]
            _uiState.value = state.copy(
                currentPath = path,
                historyIndex = newIdx
            )
            loadFiles(path)
        }
    }

    fun navigateForward() {
        val state = _uiState.value
        if (state.canGoForward) {
            val newIdx = state.historyIndex + 1
            val path = state.history[newIdx]
            _uiState.value = state.copy(
                currentPath = path,
                historyIndex = newIdx
            )
            loadFiles(path)
        }
    }

    fun navigateToBreadcrumbIndex(index: Int) {
        val parts = _uiState.value.breadcrumbParts
        val targetPath = if (index == 0) {
            "/"
        } else {
            "/" + parts.subList(1, index + 1).joinToString("/")
        }
        navigateTo(targetPath)
    }

    fun toggleFileSelection(file: RemoteFile) {
        val current = _uiState.value.selectedFiles.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _uiState.value = _uiState.value.copy(selectedFiles = current)
    }

    fun createDirectory(dirName: String) {
        val dev = _uiState.value.currentDevice ?: return
        val current = _uiState.value.currentPath
        val target = if (current.endsWith("/")) "$current$dirName" else "$current/$dirName"
        viewModelScope.launch {
            val res = fileService.createDirectory(dev, target)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(messageSnackbar = "Created directory $dirName")
                loadFiles(current)
            } else {
                _uiState.value = _uiState.value.copy(
                    messageSnackbar = "Failed to create directory: ${res.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun deleteSelectedFiles() {
        val dev = _uiState.value.currentDevice ?: return
        val filesToDelete = _uiState.value.selectedFiles.toList()
        if (filesToDelete.isEmpty()) return

        viewModelScope.launch {
            for (file in filesToDelete) {
                fileService.delete(dev, file.path, file.isDirectory)
            }
            _uiState.value = _uiState.value.copy(
                selectedFiles = emptySet(),
                messageSnackbar = "Deleted ${filesToDelete.size} items"
            )
            loadFiles(_uiState.value.currentPath)
        }
    }

    fun uploadFile(uri: Uri, filename: String) {
        val dev = _uiState.value.currentDevice ?: return
        val current = _uiState.value.currentPath
        val remotePath = if (current.endsWith("/")) "$current$filename" else "$current/$filename"

        viewModelScope.launch {
            try {
                val cr = getApplication<Application>().contentResolver
                val stream: InputStream = cr.openInputStream(uri) ?: return@launch
                val size = cr.openFileDescriptor(uri, "r")?.statSize ?: -1L

                _uiState.value = _uiState.value.copy(
                    transferProgress = Pair(0f, "Uploading $filename...")
                )

                val res = fileService.uploadFile(dev, stream, remotePath, size) { prog ->
                    _uiState.value = _uiState.value.copy(
                        transferProgress = Pair(prog, "Uploading $filename (${(prog * 100).toInt()}%)")
                    )
                }

                _uiState.value = _uiState.value.copy(transferProgress = null)
                if (res.isSuccess) {
                    _uiState.value = _uiState.value.copy(messageSnackbar = "Uploaded $filename")
                    loadFiles(current)
                } else {
                    _uiState.value = _uiState.value.copy(
                        messageSnackbar = "Upload failed: ${res.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferProgress = null,
                    messageSnackbar = "Upload failed: ${e.message}"
                )
            }
        }
    }

    fun downloadFile(file: RemoteFile, targetUri: Uri) {
        val dev = _uiState.value.currentDevice ?: return
        viewModelScope.launch {
            try {
                val cr = getApplication<Application>().contentResolver
                val stream: OutputStream = cr.openOutputStream(targetUri) ?: return@launch

                _uiState.value = _uiState.value.copy(
                    transferProgress = Pair(0f, "Downloading ${file.name}...")
                )

                val res = fileService.downloadFile(dev, file.path, stream, file.size) { prog ->
                    _uiState.value = _uiState.value.copy(
                        transferProgress = Pair(prog, "Downloading ${file.name} (${(prog * 100).toInt()}%)")
                    )
                }

                _uiState.value = _uiState.value.copy(transferProgress = null)
                if (res.isSuccess) {
                    _uiState.value = _uiState.value.copy(messageSnackbar = "Downloaded ${file.name}")
                } else {
                    _uiState.value = _uiState.value.copy(
                        messageSnackbar = "Download failed: ${res.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferProgress = null,
                    messageSnackbar = "Download failed: ${e.message}"
                )
            }
        }
    }
}
