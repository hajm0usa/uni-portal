package ir.hajmousa.uniportal.auth

import android.content.Context
import android.content.SharedPreferences
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CredentialStoreTest {
    private lateinit var preferences: SharedPreferences
    private var key: SecretKey? = null
    private var created = 0
    private var deleted = false
    private val credentials = LoginCredentials("synthetic-user", "synthetic-password")

    private fun store(prefs: SharedPreferences = preferences) = CredentialStore(prefs, { create ->
        key ?: run {
            check(create)
            created++
            SecretKeySpec(ByteArray(32) { 7 }, "AES").also { key = it }
        }
    }, { deleted = true; key = null })

    @Before
    fun start() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("credentials-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        AppLog.clear()
    }

    @After
    fun stop() { preferences.edit().clear().commit(); AppLog.clear() }

    @Test
    fun `new store instance restores encrypted credentials and never creates a key for absent data`() {
        assertNull(store().read().getOrThrow())
        assertEquals(0, created)
        store().save(credentials).getOrThrow()
        val restored = store().read().getOrThrow()!!
        assertEquals(credentials.username, restored.username)
        assertEquals(credentials.password, restored.password)
        assertEquals(1, created)
        assertFalse(preferences.all.toString().contains(credentials.username))
        assertFalse(preferences.all.toString().contains(credentials.password))
    }

    @Test
    fun `forgetting removes both key and persisted credentials`() {
        store().save(credentials).getOrThrow()
        store().clear().getOrThrow()
        assertTrue(deleted)
        assertNull(key)
        assertTrue(preferences.all.isEmpty())
        assertNull(store().read().getOrThrow())
    }

    @Test
    fun `missing keys and corrupt ciphertext fail closed without regenerating keys or logging secrets`() {
        store().save(credentials).getOrThrow()
        key = null
        val missing = store().read().exceptionOrNull() as PortalException
        assertEquals(PortalException.Reason.STORAGE_ERROR, missing.reason)
        assertEquals(1, created)
        preferences.edit().putString("encrypted", "private-example-input").commit()
        assertTrue(store().read().isFailure)
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
        assertFalse(AppLog.snapshot().toString().contains(credentials.password))
    }

    @Test
    fun `failed persistence is reported and forget destroys the key even if removal fails`() {
        store().save(credentials).getOrThrow()
        val failing = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by preferences.edit() {
                override fun putString(key: String, value: String?): SharedPreferences.Editor = this
                override fun clear(): SharedPreferences.Editor = this
                override fun commit() = false
            }
        }
        assertTrue(store(failing).save(credentials).isFailure)
        assertTrue(store(failing).clear().isFailure)
        assertTrue(deleted)
        assertNull(key)
        assertTrue(store().read().isFailure)
    }
}
