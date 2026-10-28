package org.webosbrew.devmanager.data.model

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isLink: Boolean = false,
    val size: Long = 0L,
    val mtime: Long = 0L,
    val permissions: String = ""
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "-"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$size B"
            }
        }
}
