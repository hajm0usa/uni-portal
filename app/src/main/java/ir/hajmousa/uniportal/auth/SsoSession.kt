package ir.hajmousa.uniportal.auth

import android.content.Context
import android.webkit.CookieManager
import ir.hajmousa.uniportal.core.AppLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class SsoSession(context: Context) {
    val cookieJar = PersistentCookieJar(context.applicationContext)
    val credentials = CredentialStore(context.applicationContext)

    fun importWebViewCookies(cookieManager: CookieManager, isCurrent: () -> Boolean): Boolean = synchronized(cookieBridgeLock) {
        if (!isCurrent()) return@synchronized false
        try {
            val moodleUrl = SsoHosts.MOODLE_URL.toHttpUrl()
            val ssoUrl = "https://${SsoHosts.SSO_HOST}/".toHttpUrl()
            cookieJar.importWebViewCookies(ssoUrl, cookieManager.getCookie(ssoUrl.toString()))
            cookieJar.importWebViewCookies(moodleUrl, cookieManager.getCookie(moodleUrl.toString()))
            cookieJar.loadForRequest(moodleUrl).any { it.name == SsoHosts.MOODLE_SESSION_COOKIE }
        } catch (_: Exception) {
            AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
            false
        }
    }

    suspend fun signOut(): Boolean {
        val forgotten = withContext(Dispatchers.IO) { credentials.clear().isSuccess }
        val cleared = clearCookies()
        return forgotten && cleared
    }

    suspend fun clearCookies(): Boolean {
        val nativeCleared = withContext(Dispatchers.IO) {
            try {
                synchronized(cookieBridgeLock) { cookieJar.clearAll() }
                true
            } catch (_: Exception) {
                AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
                false
            }
        }
        val browserCleared = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies {
                        val flushed = runCatching { cookieManager.flush() }.isSuccess
                        if (continuation.isActive) continuation.resume(flushed)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
        if (!browserCleared) AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
        return nativeCleared && browserCleared
    }

    private companion object {
        // A disposed login must not restore its cookies after a new login/logout clears them.
        val cookieBridgeLock = Any()
    }
}
