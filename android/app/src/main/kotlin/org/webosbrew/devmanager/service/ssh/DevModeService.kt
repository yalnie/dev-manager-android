package org.webosbrew.devmanager.service.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.model.DevModeStatus
import java.util.concurrent.TimeUnit

class DevModeService(
    private val ssh: SshConnectionManager = SshConnectionManager.getInstance(),
    private val luna: LunaService = LunaService.getInstance()
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(device: Device): Result<DevModeStatus> = withContext(Dispatchers.IO) {
        if (device.username != "prisoner") {
            return@withContext Result.success(DevModeStatus(token = null, remaining = null))
        }

        try {
            val cmd = ssh.executeCommand(device, "cat /var/luna/preferences/devmode_enabled")
            val token = cmd.stdout.trim()
            if (token.isEmpty() || !token.matches("^[0-9a-zA-Z]+$".toRegex())) {
                return@withContext Result.success(DevModeStatus(token = null, remaining = null))
            }

            val url = "https://developer.lge.com/secure/CheckDevModeSession.dev?sessionToken=$token"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(DevModeStatus(token = token, remaining = null))
            }

            val body = response.body?.string() ?: ""
            val jsonElement = json.parseToJsonElement(body).jsonObject
            val result = jsonElement["result"]?.jsonPrimitive?.content
            val errorMsg = jsonElement["errorMsg"]?.jsonPrimitive?.content

            val remaining = if (result == "success") errorMsg else null
            Result.success(DevModeStatus(token = token, remaining = remaining))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renewSession(device: Device): Result<Unit> {
        return luna.extendDevMode(device)
    }

    companion object {
        @Volatile
        private var instance: DevModeService? = null

        fun getInstance(): DevModeService {
            return instance ?: synchronized(this) {
                instance ?: DevModeService().also { instance = it }
            }
        }
    }
}
