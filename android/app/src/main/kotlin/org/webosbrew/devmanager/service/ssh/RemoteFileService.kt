package org.webosbrew.devmanager.service.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webosbrew.devmanager.data.model.Device
import org.webosbrew.devmanager.data.model.RemoteFile
import java.io.InputStream
import java.io.OutputStream
import java.util.Vector

class RemoteFileService(private val ssh: SshConnectionManager = SshConnectionManager.getInstance()) {

    suspend fun listFiles(device: Device, path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            ssh.withSftp(device) { sftp ->
                val list = Vector<ChannelSftp.LsEntry>()
                @Suppress("UNCHECKED_CAST")
                val entries = sftp.ls(path) as Vector<ChannelSftp.LsEntry>

                val result = entries.filterNot { it.filename == "." || it.filename == ".." }.map { entry ->
                    val isDir = entry.attrs.isDir
                    val isLink = entry.attrs.isLink
                    val fullPath = if (path.endsWith("/")) "$path${entry.filename}" else "$path/${entry.filename}"

                    RemoteFile(
                        name = entry.filename,
                        path = fullPath,
                        isDirectory = isDir,
                        isLink = isLink,
                        size = entry.attrs.size,
                        mtime = entry.attrs.mTime * 1000L,
                        permissions = entry.attrs.permissionsString
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(device: Device, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ssh.withSftp(device) { sftp ->
                sftp.mkdir(path)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(device: Device, path: String, isDirectory: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isDirectory) {
                // Use rm -rf via command exec for recursive directory deletion
                val escaped = path.replace("'", "'\\''")
                val cmd = ssh.executeCommand(device, "rm -rf '$escaped'")
                if (cmd.exitCode != 0) {
                    return@withContext Result.failure(Exception("Failed to delete directory: ${cmd.stderr}"))
                }
            } else {
                ssh.withSftp(device) { sftp ->
                    sftp.rm(path)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        device: Device,
        remotePath: String,
        outputStream: OutputStream,
        totalSize: Long = -1L,
        onProgress: (progress: Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ssh.withSftp(device) { sftp ->
                val monitor = object : SftpProgressMonitor {
                    private var current = 0L

                    override fun init(op: Int, src: String?, dest: String?, max: Long) {
                        current = 0L
                    }

                    override fun count(count: Long): Boolean {
                        current += count
                        val total = if (totalSize > 0) totalSize else 1L
                        onProgress((current.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        return true
                    }

                    override fun end() {
                        onProgress(1f)
                    }
                }

                outputStream.use { out ->
                    sftp.get(remotePath, out, monitor)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        device: Device,
        inputStream: InputStream,
        remotePath: String,
        totalSize: Long = -1L,
        onProgress: (progress: Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ssh.withSftp(device) { sftp ->
                val monitor = object : SftpProgressMonitor {
                    private var current = 0L

                    override fun init(op: Int, src: String?, dest: String?, max: Long) {
                        current = 0L
                    }

                    override fun count(count: Long): Boolean {
                        current += count
                        val total = if (totalSize > 0) totalSize else 1L
                        onProgress((current.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        return true
                    }

                    override fun end() {
                        onProgress(1f)
                    }
                }

                inputStream.use { stream ->
                    sftp.put(stream, remotePath, monitor)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: RemoteFileService? = null

        fun getInstance(): RemoteFileService {
            return instance ?: synchronized(this) {
                instance ?: RemoteFileService().also { instance = it }
            }
        }
    }
}
