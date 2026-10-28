package org.webosbrew.devmanager.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AuthType {
    KEY,
    PASSWORD
}

@Serializable
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 9922,
    val username: String = "prisoner",
    val authType: AuthType = AuthType.KEY,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val password: String? = null,
    val isDefault: Boolean = false,
    val description: String? = null
)
