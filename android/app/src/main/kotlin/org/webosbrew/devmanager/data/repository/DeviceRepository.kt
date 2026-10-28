package org.webosbrew.devmanager.data.repository

import android.content.Context
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webosbrew.devmanager.data.model.AuthType
import org.webosbrew.devmanager.data.model.Device
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class DeviceRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val devicesFile: File by lazy {
        File(context.filesDir, "devices.json")
    }

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        try {
            if (devicesFile.exists()) {
                val content = devicesFile.readText()
                val list = json.decodeFromString<List<Device>>(content)
                _devices.value = list
                val defaultDev = list.firstOrNull { it.isDefault } ?: list.firstOrNull()
                _selectedDevice.value = defaultDev
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _devices.value = emptyList()
            _selectedDevice.value = null
        }
    }

    private fun saveDevices(list: List<Device>) {
        _devices.value = list
        try {
            devicesFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addDevice(device: Device) = withContext(Dispatchers.IO) {
        val current = _devices.value.toMutableList()
        // If this is the only device or marked default, set it
        val makeDefault = device.isDefault || current.isEmpty()
        val toSave = if (makeDefault) {
            current.map { it.copy(isDefault = false) } + device.copy(isDefault = true)
        } else {
            current + device
        }
        saveDevices(toSave)
        if (makeDefault) {
            _selectedDevice.value = device.copy(isDefault = true)
        }
    }

    suspend fun updateDevice(device: Device) = withContext(Dispatchers.IO) {
        val current = _devices.value.map {
            if (it.id == device.id) device else it
        }
        saveDevices(current)
        if (_selectedDevice.value?.id == device.id) {
            _selectedDevice.value = device
        }
    }

    suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val current = _devices.value.filterNot { it.id == deviceId }
        saveDevices(current)
        if (_selectedDevice.value?.id == deviceId) {
            _selectedDevice.value = current.firstOrNull { it.isDefault } ?: current.firstOrNull()
        }
    }

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
    }

    suspend fun setDefaultDevice(deviceId: String) = withContext(Dispatchers.IO) {
        val current = _devices.value.map {
            it.copy(isDefault = it.id == deviceId)
        }
        saveDevices(current)
        _selectedDevice.value = current.firstOrNull { it.id == deviceId }
    }

    suspend fun fetchDevModeKey(host: String, passphrase: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "http://$host:9991/webos_rsa"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch key from TV (HTTP ${response.code}). Make sure Key Server is ON in Dev Mode app."))
            }
            val keyPem = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty key received from TV"))

            // Verify passphrase if provided
            if (passphrase.isNotEmpty()) {
                val jsch = com.jcraft.jsch.JSch()
                try {
                    val keyPair = KeyPair.load(jsch, keyPem.toByteArray(Charsets.UTF_8), null)
                    if (keyPair.isEncrypted && !keyPair.decrypt(passphrase)) {
                        return@withContext Result.failure(Exception("Incorrect passphrase. Please verify the 6-character passphrase shown on TV."))
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(Exception("Invalid key or passphrase: ${e.message}"))
                }
            }

            Result.success(keyPem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkPort(host: String, port: Int, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        private var instance: DeviceRepository? = null

        fun getInstance(context: Context): DeviceRepository {
            return instance ?: synchronized(this) {
                instance ?: DeviceRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
