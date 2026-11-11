package ir.hajmousa.uniportal.auth

import java.util.Base64
import javax.crypto.SecretKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Not a data class: generated toString/copy must not expose credentials.
@Serializable
class LoginCredentials(val username: String, val password: String) {
    init {
        require(username.isNotBlank() && username.length <= 1024)
        require(password.isNotEmpty() && password.length <= 4096)
    }

    override fun toString() = "LoginCredentials(redacted)"
}

internal object CredentialCipher {
    fun seal(key: SecretKey, credentials: LoginCredentials): String {
        val plain = Json.encodeToString(credentials).encodeToByteArray()
        return try {
            val sealed = AesGcm.seal(key, plain)
            Base64.getEncoder().encodeToString(sealed.iv + sealed.ciphertext)
        } finally {
            plain.fill(0)
        }
    }

    fun open(key: SecretKey, blob: String): LoginCredentials {
        require(blob.length <= 65536)
        val bytes = Base64.getDecoder().decode(blob)
        require(bytes.size > 12 + 16)
        val plain = AesGcm.open(key, SealedData(bytes.copyOfRange(0, 12), bytes.copyOfRange(12, bytes.size)))
        return try {
            Json.decodeFromString<LoginCredentials>(plain.decodeToString())
        } finally {
            plain.fill(0)
        }
    }
}
