package ir.hajmousa.uniportal.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ir.hajmousa.uniportal.core.AppLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class PersistentCookieJar(context: Context) : CookieJar {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        writeRecords(CookieJarLogic.save(readRecords(), url, cookies, System.currentTimeMillis()))
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val records = readRecords()
        val loaded = CookieJarLogic.load(records, url, System.currentTimeMillis())
        if (loaded.records != records) writeRecords(loaded.records)
        return loaded.cookies
    }

    @Synchronized
    fun clear(host: String) {
        writeRecords(CookieJarLogic.clear(readRecords(), host))
    }

    @Synchronized
    fun clearAll() {
        if (!preferences.edit().remove(COOKIES).commit()) failStorage()
    }

    @Synchronized
    fun importWebViewCookies(url: HttpUrl, cookieHeader: String?) {
        saveFromResponse(url, WebViewCookieParser.parse(url, cookieHeader))
    }

    private fun readRecords(): List<CookieRecord> {
        val blob = preferences.getString(COOKIES, null) ?: return emptyList()
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val ivSize = bytes[0].toInt() and 0xff
            require(ivSize == GCM_IV_BYTES && bytes.size > 1 + ivSize)
            val plain = AesGcm.open(
                key(),
                SealedData(bytes.copyOfRange(1, 1 + ivSize), bytes.copyOfRange(1 + ivSize, bytes.size)),
            )
            Json.decodeFromString<List<CookieRecord>>(plain.decodeToString())
        } catch (_: Exception) {
            AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
            preferences.edit().remove(COOKIES).commit()
            emptyList()
        }
    }

    private fun writeRecords(records: List<CookieRecord>) {
        if (records.isEmpty()) {
            if (!preferences.edit().remove(COOKIES).commit()) failStorage()
            return
        }
        try {
            val encrypted = AesGcm.seal(key(), Json.encodeToString(records).encodeToByteArray())
            val blob = byteArrayOf(encrypted.iv.size.toByte()) + encrypted.iv + encrypted.ciphertext
            val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
            if (!preferences.edit().putString(COOKIES, encoded).commit()) failStorage()
        } catch (_: Exception) {
            failStorage()
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun failStorage(): Nothing {
        AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
        throw IllegalStateException("Encrypted cookie storage unavailable")
    }

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { toHttpUrl() }.getOrNull()

    private companion object {
        const val PREFERENCES = "encrypted_cookie_jar"
        const val COOKIES = "cookies"
        const val KEY_ALIAS = "uni-portal-cookie-jar-v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_IV_BYTES = 12
    }
}
