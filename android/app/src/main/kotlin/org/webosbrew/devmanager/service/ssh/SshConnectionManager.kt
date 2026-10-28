package org.webosbrew.devmanager.service.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webosbrew.devmanager.data.model.AuthType
import org.webosbrew.devmanager.data.model.Device
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

data class CommandOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface ShellSession {
    fun sendInput(input: String)
    fun sendBytes(bytes: ByteArray)
    fun resize(cols: Int, rows: Int, widthPx: Int = 0, heightPx: Int = 0)
    fun close()
}

class SshConnectionManager {

    suspend fun createSession(device: Device): Session = withContext(Dispatchers.IO) {
        val jsch = JSch()

        if (device.authType == AuthType.KEY && !device.privateKey.isNullOrEmpty()) {
            val keyBytes = device.privateKey.toByteArray(Charsets.UTF_8)
            val passBytes = device.passphrase?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("device_key", keyBytes, null, passBytes)
        }

        val session = jsch.getSession(device.username, device.host, device.port)
        if (device.authType == AuthType.PASSWORD && !device.password.isNullOrEmpty()) {
            session.setPassword(device.password)
        }

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        config["PreferredAuthentications"] = if (device.authType == AuthType.KEY) "publickey,password" else "password,keyboard-interactive"
        session.setConfig(config)
        session.timeout = 15000
        session.connect()
        session
    }

    suspend fun executeCommand(device: Device, command: String): CommandOutput = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelExec? = null
        try {
            session = createSession(device)
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)

            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()
            channel.outputStream = stdoutStream
            channel.setErrStream(stderrStream)

            channel.connect(15000)

            while (!channel.isClosed) {
                Thread.sleep(50)
            }

            val exitCode = channel.exitStatus
            CommandOutput(
                exitCode = exitCode,
                stdout = stdoutStream.toString(Charsets.UTF_8.name()),
                stderr = stderrStream.toString(Charsets.UTF_8.name())
            )
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun <T> withSftp(device: Device, block: suspend (ChannelSftp) -> T): T = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession(device)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(15000)
            block(channel)
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun openShell(
        device: Device,
        onOutput: (String) -> Unit
    ): ShellSession = withContext(Dispatchers.IO) {
        val session = createSession(device)
        val channel = session.openChannel("shell") as ChannelShell
        channel.setPtyType("xterm-256color")
        channel.setPtySize(80, 24, 640, 480)

        val inputStream: InputStream = channel.inputStream
        val outputStream: OutputStream = channel.outputStream

        channel.connect(15000)

        val readerThread = Thread {
            val buffer = ByteArray(2048)
            try {
                while (channel.isConnected) {
                    val read = inputStream.read(buffer)
                    if (read > 0) {
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        onOutput(text)
                    } else if (read == -1) {
                        break
                    }
                }
            } catch (_: Exception) {}
        }
        readerThread.isDaemon = true
        readerThread.start()

        object : ShellSession {
            override fun sendInput(input: String) {
                try {
                    outputStream.write(input.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun sendBytes(bytes: ByteArray) {
                try {
                    outputStream.write(bytes)
                    outputStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun resize(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
                try {
                    channel.setPtySize(cols, rows, widthPx, heightPx)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun close() {
                try { channel.disconnect() } catch (_: Exception) {}
                try { session.disconnect() } catch (_: Exception) {}
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SshConnectionManager? = null

        fun getInstance(): SshConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: SshConnectionManager().also { instance = it }
            }
        }
    }
}
