package org.webosbrew.devmanager.service.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webosbrew.devmanager.data.model.IpkHash
import org.webosbrew.devmanager.data.model.RepoPackage
import java.util.concurrent.TimeUnit

class HomebrewRepoService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getApps(): Result<List<RepoPackage>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://repo.webosbrew.org/api/apps.json"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch repository: HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty repo body"))
            val root = json.parseToJsonElement(body).jsonObject
            val packagesArray = root["packages"]?.jsonArray ?: return@withContext Result.success(emptyList())

            val list = packagesArray.mapNotNull { element ->
                try {
                    val pkg = element.jsonObject
                    val id = pkg["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = pkg["title"]?.jsonPrimitive?.content ?: id
                    val manifest = pkg["manifest"]?.jsonObject
                    val version = manifest?.get("version")?.jsonPrimitive?.content ?: "1.0.0"
                    val type = manifest?.get("type")?.jsonPrimitive?.content
                    val description = manifest?.get("appDescription")?.jsonPrimitive?.content
                    val summary = pkg["summary"]?.jsonPrimitive?.content
                    val iconUri = manifest?.get("iconUri")?.jsonPrimitive?.content
                    val sourceUrl = manifest?.get("sourceUrl")?.jsonPrimitive?.content
                    val ipkUrl = manifest?.get("ipkUrl")?.jsonPrimitive?.content
                    val hashObj = manifest?.get("ipkHash")?.jsonObject
                    val sha256 = hashObj?.get("sha256")?.jsonPrimitive?.content

                    RepoPackage(
                        id = id,
                        title = title,
                        version = version,
                        type = type,
                        description = description,
                        summary = summary,
                        iconUri = iconUri,
                        sourceUrl = sourceUrl,
                        ipkUrl = ipkUrl,
                        ipkHash = if (sha256 != null) IpkHash(sha256) else null
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: HomebrewRepoService? = null

        fun getInstance(): HomebrewRepoService {
            return instance ?: synchronized(this) {
                instance ?: HomebrewRepoService().also { instance = it }
            }
        }
    }
}
