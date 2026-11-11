package ir.hajmousa.uniportal.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CredentialStore internal constructor(
    private val preferences: SharedPreferences,
    private val key: (Boolean) -> SecretKey,
    private val deleteKey: () -> Unit,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("sso_credentials", Context.MODE_PRIVATE),
        ::androidKey,
        {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        },
    )

    fun read(): Result<LoginCredentials?> = safely {
        preferences.getString("encrypted", null)?.let { CredentialCipher.open(key(false), it) }
    }

    fun save(credentials: LoginCredentials): Result<Unit> = safely {
        val blob = CredentialCipher.seal(key(true), credentials)
        check(preferences.edit().putString("encrypted", blob).commit())
    }

    fun clear(): Result<Unit> = safely {
        // Delete the key first so a failed file deletion cannot leave decryptable credentials.
        deleteKey()
        check(preferences.edit().clear().commit())
    }

    private fun <T> safely(block: () -> T): Result<T> = synchronized(storageLock) {
        try {
            Result.success(block())
        } catch (_: Exception) {
            AppLog.record(AppLog.Event.CREDENTIAL_STORAGE_FAILED)
            Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
        }
    }

    private companion object {
        const val KEY_ALIAS = "uni-portal-sso-credentials-v1"
        val storageLock = Any()

        fun androidKey(create: Boolean): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            check(create)
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
                generateKey()
            }
        }
    }
}
