package ir.hajmousa.uniportal.auth

import android.content.Context
import android.webkit.CookieManager
import ir.hajmousa.uniportal.core.AppLog
import okhttp3.HttpUrl.Companion.toHttpUrl

class SsoSession(context: Context) {
    val cookieJar = PersistentCookieJar(context.applicationContext)

    fun importWebViewCookies(cookieManager: CookieManager): Boolean {
        return try {
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

    fun signOut(cookieManager: CookieManager, onComplete: () -> Unit) {
        runCatching { cookieJar.clearAll() }
        cookieManager.removeAllCookies {
            cookieManager.flush()
            android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete)
        }
    }
}
