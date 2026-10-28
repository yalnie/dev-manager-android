package org.webosbrew.devmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RepoPackage(
    val id: String,
    val title: String,
    val version: String,
    val type: String? = null,
    val description: String? = null,
    val summary: String? = null,
    val iconUri: String? = null,
    val sourceUrl: String? = null,
    val ipkUrl: String? = null,
    val ipkHash: IpkHash? = null,
    val minOsVersion: String? = null,
    val maxOsVersion: String? = null,
    val hasUpdate: Boolean = false
)

@Serializable
data class IpkHash(
    val sha256: String? = null
)
