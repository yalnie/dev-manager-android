package org.webosbrew.devmanager.service.ssh

import android.content.Context
import android.net.Uri
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webosbrew.devmanager.data.model.Device
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppInstallService(
    private val ssh: SshConnectionManager = SshConnectionManager.getInstance(),
    private val luna: LunaService = LunaService.getInstance()
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun installLocalIpk(
        context: Context,
        device: Device,
        uri: Uri,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempRemoteDir = "/media/developer/temp"
        val remoteFileName = "devman_${UUID.randomUUID()}.ipk"
        val remoteFilePath = "$tempRemoteDir/$remoteFileName"

        try {
            onProgress(0f, "Preparing transfer...")

            // Ensure remote directory exists
            ssh.withSftp(device) { sftp ->
                try {
                    sftp.mkdir(tempRemoteDir)
                } catch (_: Exception) {}
            }

            // Get content stream and total size
            val contentResolver = context.contentResolver
            val fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
            val inputStream: InputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file from selected URI"))

            onProgress(0.05f, "Uploading package to TV...")

            ssh.withSftp(device) { sftp ->
                val monitor = object : SftpProgressMonitor {
                    private var transferred: Long = 0L

                    override fun init(op: Int, src: String?, dest: String?, max: Long) {
                        transferred = 0L
                    }

                    override fun count(count: Long): Boolean {
                        transferred += count
                        val total = if (fileSize > 0) fileSize else 1L
                        val ratio = (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        onProgress(0.05f + ratio * 0.75f, "Uploading: ${(ratio * 100).toInt()}%")
                        return true
                    }

                    override fun end() {}
                }

                inputStream.use { stream ->
                    sftp.put(stream, remoteFilePath, monitor)
                }
            }

            onProgress(0.85f, "Installing package on TV...")

            // Execute install via Luna
            val installParam = "{\"id\":\"org.webosbrew.install\",\"ipkUrl\":\"$remoteFilePath\",\"subscribe\":false}"
            val res = luna.call(device, "luna://com.webos.appInstallService/dev/install", installParam)

            onProgress(1.0f, "Finishing...")
            if (res.isFailure) {
                return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Installation failed"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Clean up temporary IPK
            try {
                ssh.withSftp(device) { sftp ->
                    try {
                        sftp.rm(remoteFilePath)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun installFromUrl(
        context: Context,
        device: Device,
        ipkUrl: String,
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "download_${UUID.randomUUID()}.ipk")
        try {
            onProgress(0.05f, "Downloading package...")

            val request = Request.Builder().url(ipkUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download IPK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty package body"))
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var current: Long = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        current += read
                        if (totalBytes > 0) {
                            val ratio = (current.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(0.05f + ratio * 0.35f, "Downloading: ${(ratio * 100).toInt()}%")
                        }
                    }
                }
            }

            // Now install using local file
            val fileUri = Uri.fromFile(tempFile)
            installLocalIpk(context, device, fileUri) { prog, status ->
                onProgress(0.4f + prog * 0.6f, status)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    companion object {
        @Volatile
        private var instance: AppInstallService? = null

        fun getInstance(): AppInstallService {
            return instance ?: synchronized(this) {
                instance ?: AppInstallService().also { instance = it }
            }
        }
    }
}
