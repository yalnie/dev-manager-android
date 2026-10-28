package org.webosbrew.devmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PackageInfo(
    val id: String,
    val title: String,
    val version: String,
    val appDescription: String? = null,
    val vendor: String? = null,
    val icon: String? = null,
    val folderPath: String? = null,
    val type: String? = null,
    val visible: Boolean = true,
    val removable: Boolean = true,
    val systemApp: Boolean = false,
    val iconUri: String? = null
)
