package ir.hajmousa.uniportal.auth

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SealedData(val iv: ByteArray, val ciphertext: ByteArray)

internal object AesGcm {
    fun seal(key: SecretKey, plaintext: ByteArray): SealedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return SealedData(cipher.iv, cipher.doFinal(plaintext))
    }

    fun open(key: SecretKey, sealed: SealedData): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }
}
