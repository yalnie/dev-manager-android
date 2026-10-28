package org.webosbrew.devmanager.service.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.model.DeviceInfo
import org.webosbrew.devmanager.data.model.PackageInfo
import org.webosbrew.devmanager.data.model.StorageInfo

class LunaService(private val ssh: SshConnectionManager = SshConnectionManager.getInstance()) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun call(
        device: Device,
        uri: String,
        paramsJson: String = "{}",
        isPub: Boolean = true
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        val sendCmd = if (isPub) "luna-send-pub" else "luna-send"
        val escapedJson = paramsJson.replace("'", "'\\''")
        val command = "$sendCmd -n 1 $uri '$escapedJson'"

        val output = ssh.executeCommand(device, command)
        val text = output.stdout.trim()
        if (text.isEmpty()) {
            return@withContext Result.failure(Exception("Empty response from Luna call ($command): ${output.stderr}"))
        }

        try {
            val jsonObject = json.parseToJsonElement(text).jsonObject
            val returnValue = jsonObject["returnValue"]?.jsonPrimitive?.booleanOrNull
            if (returnValue == false) {
                val errorText = jsonObject["errorText"]?.jsonPrimitive?.content ?: "Luna call failed"
                return@withContext Result.failure(Exception(errorText))
            }
            Result.success(jsonObject)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse Luna response: $text", e))
        }
    }

    suspend fun listApps(device: Device): Result<List<PackageInfo>> = withContext(Dispatchers.IO) {
        val primaryCall = call(device, "luna://com.webos.applicationManager/dev/listApps")
        val response = if (primaryCall.isSuccess) {
            primaryCall
        } else {
            // Fallback for older or rooted devices
            call(device, "luna://com.webos.applicationManager/listApps", isPub = false)
        }

        response.map { obj ->
            val appsArray = obj["apps"]?.jsonArray ?: JsonArray(emptyList())
            appsArray.mapNotNull { element ->
                try {
                    val app = element.jsonObject
                    val id = app["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = app["title"]?.jsonPrimitive?.content ?: id
                    val version = app["version"]?.jsonPrimitive?.content ?: "1.0.0"
                    val vendor = app["vendor"]?.jsonPrimitive?.content
                    val icon = app["icon"]?.jsonPrimitive?.content
                    val folderPath = app["folderPath"]?.jsonPrimitive?.content
                    val type = app["type"]?.jsonPrimitive?.content
                    val visible = app["visible"]?.jsonPrimitive?.booleanOrNull ?: true
                    val removable = app["removable"]?.jsonPrimitive?.booleanOrNull ?: true
                    val systemApp = app["systemApp"]?.jsonPrimitive?.booleanOrNull ?: false

                    PackageInfo(
                        id = id,
                        title = title,
                        version = version,
                        vendor = vendor,
                        icon = icon,
                        folderPath = folderPath,
                        type = type,
                        visible = visible,
                        removable = removable,
                        systemApp = systemApp
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.title.lowercase() }
        }
    }

    suspend fun launchApp(device: Device, appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val param = "{\"id\":\"$appId\"}"
        call(device, "luna://com.webos.applicationManager/launch", param).map { }
    }

    suspend fun closeApp(device: Device, appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val param = "{\"id\":\"$appId\"}"
        val res = call(device, "luna://com.webos.applicationManager/closeByAppId", param)
        if (res.isSuccess) return@withContext Result.success(Unit)
        // Fallback
        call(device, "luna://com.webos.applicationManager/close", param).map { }
    }

    suspend fun removeApp(device: Device, appId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val param = "{\"id\":\"$appId\"}"
        call(device, "luna://com.webos.appInstallService/dev/remove", param).map { }
    }

    suspend fun getDeviceInfo(device: Device): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val sysInfoCall = call(
                device,
                "luna://com.webos.service.tv.systemproperty/getSystemInfo",
                "{\"keys\":[\"firmwareVersion\",\"modelName\",\"sdkVersion\",\"otaId\"]}"
            )
            val sysObj = sysInfoCall.getOrNull()

            val osInfoCall = call(
                device,
                "luna://com.palm.systemservice/osInfo/query",
                "{\"parameters\":[\"device_name\",\"webos_manufacturing_version\",\"webos_release\"]}"
            )
            val osObj = osInfoCall.getOrNull()

            val modelName = sysObj?.get("modelName")?.jsonPrimitive?.content ?: "LG webOS TV"
            val firmwareVersion = sysObj?.get("firmwareVersion")?.jsonPrimitive?.content ?: ""
            val sdkVersion = sysObj?.get("sdkVersion")?.jsonPrimitive?.content
            val otaId = sysObj?.get("otaId")?.jsonPrimitive?.content

            val webosRelease = osObj?.get("webos_release")?.jsonPrimitive?.content
            val deviceName = osObj?.get("device_name")?.jsonPrimitive?.content

            val osVersion = webosRelease ?: sdkVersion ?: "Unknown"

            Result.success(
                DeviceInfo(
                    modelName = modelName,
                    osVersion = osVersion,
                    otaId = otaId,
                    firmwareVersion = firmwareVersion,
                    socName = deviceName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStorageInfo(device: Device, mountPoint: String = "/media/developer"): Result<StorageInfo> = withContext(Dispatchers.IO) {
        try {
            val output = ssh.executeCommand(device, "df $mountPoint")
            val lines = output.stdout.trim().lines()
            if (lines.size < 2) {
                // Try df /
                val fallbackOutput = ssh.executeCommand(device, "df /")
                val fallbackLines = fallbackOutput.stdout.trim().lines()
                if (fallbackLines.size < 2) {
                    return@withContext Result.failure(Exception("Unable to read storage info: ${output.stderr}"))
                }
                return@withContext parseDfLine(fallbackLines[1])
            }
            parseDfLine(lines[1])
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDfLine(line: String): Result<StorageInfo> {
        return try {
            val parts = line.split("\\s+".toRegex())
            // Filesystem 1K-blocks Used Available Use% Mounted
            val total = parts[1].toLongOrNull() ?: 0L
            val used = parts[2].toLongOrNull() ?: 0L
            val available = parts[3].toLongOrNull() ?: 0L
            Result.success(StorageInfo(total, used, available))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extendDevMode(device: Device): Result<Unit> = withContext(Dispatchers.IO) {
        val param = "{\"id\":\"com.palmdts.devmode\",\"subscribe\":false,\"params\":{\"extend\":true}}"
        call(device, "luna://com.webos.applicationManager/launch", param).map { }
    }

    suspend fun getHomebrewChannelConfig(device: Device): Result<Boolean> = withContext(Dispatchers.IO) {
        val call = call(device, "luna://org.webosbrew.hbchannel.service/getConfiguration", "{}")
        call.map { it["root"]?.jsonPrimitive?.booleanOrNull ?: false }
    }

    companion object {
        @Volatile
        private var instance: LunaService? = null

        fun getInstance(): LunaService {
            return instance ?: synchronized(this) {
                instance ?: LunaService().also { instance = it }
            }
        }
    }
}
