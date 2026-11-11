package ir.hajmousa.uniportal.auth

import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class LoginCredentialsTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun `encrypted credentials round trip preserve password whitespace without plaintext at rest`() {
        val original = LoginCredentials("synthetic-user", "  synthetic-password\n\"\\  ")
        val first = CredentialCipher.seal(key, original)
        val second = CredentialCipher.seal(key, original)
        assertNotEquals(first, second)
        val decoded = CredentialCipher.open(key, first)
        assertEquals(original.username, decoded.username)
        assertEquals(original.password, decoded.password)
        val storedBytes = Base64.getDecoder().decode(first).decodeToString()
        assertFalse(storedBytes.contains(original.username))
        assertFalse(storedBytes.contains(original.password))
        assertEquals("LoginCredentials(redacted)", original.toString())
    }

    @Test
    fun `wrong key tampering truncated and oversized blobs fail closed`() {
        val blob = CredentialCipher.seal(key, LoginCredentials("synthetic-user", "synthetic-password"))
        val changed = Base64.getDecoder().decode(blob).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val invalid = listOf("", "broken!", Base64.getEncoder().encodeToString(ByteArray(20)),
            Base64.getEncoder().encodeToString(changed), "A".repeat(65537))
        invalid.forEach { assertTrue(runCatching { CredentialCipher.open(key, it) }.isFailure) }
        assertTrue(runCatching { CredentialCipher.open(SecretKeySpec(ByteArray(32) { 42 }, "AES"), blob) }.isFailure)
    }

    @Test
    fun `blank or unreasonable credentials are rejected without including their values in errors`() {
        listOf("" to "x", "  " to "x", "x" to "", "x".repeat(1025) to "x", "x" to "x".repeat(4097)).forEach { (user, password) ->
            assertTrue(runCatching { LoginCredentials(user, password) }.isFailure)
        }
    }
}
