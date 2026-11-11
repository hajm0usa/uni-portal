package ir.hajmousa.uniportal.auth

import java.net.URI

internal object SsoHosts {
    const val MOODLE_HOST = "elearn.ut.ac.ir"
    const val SSO_HOST = "sso1.ut.ac.ir"
    const val MOODLE_URL = "https://elearn.ut.ac.ir/"
    const val CALLBACK_PATH = "/admin/oauth2callback.php"
    const val MOODLE_SESSION_COOKIE = "MoodleSession"

    fun isAllowed(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.lowercase() in setOf(MOODLE_HOST, SSO_HOST)

    fun isCallback(uri: URI): Boolean =
        uri.host?.equals(MOODLE_HOST, ignoreCase = true) == true && uri.path == CALLBACK_PATH

    fun isExternalBrowserUrl(uri: URI): Boolean =
        (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            uri.host != null && uri.userInfo == null
}
