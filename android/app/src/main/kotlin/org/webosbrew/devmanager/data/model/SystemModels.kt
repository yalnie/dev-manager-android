package org.webosbrew.devmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val modelName: String,
    val osVersion: String? = null,
    val otaId: String? = null,
    val firmwareVersion: String = "",
    val socName: String? = null
)

@Serializable
data class DevModeStatus(
    val token: String? = null,
    val remaining: String? = null
)

@Serializable
data class StorageInfo(
    val total: Long = 0L,
    val used: Long = 0L,
    val available: Long = 0L
) {
    val usedRatio: Float
        get() = if (total > 0L) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedUsed: String
        get() = formatBytes(used * 1024L)

    val formattedTotal: String
        get() = formatBytes(total * 1024L)

    val formattedAvailable: String
        get() = formatBytes(available * 1024L)

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
