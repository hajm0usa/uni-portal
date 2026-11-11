package ir.hajmousa.uniportal.auth

import okhttp3.Cookie
import okhttp3.HttpUrl

internal object WebViewCookieParser {
    // CookieManager exposes only name/value pairs, not the original domain, path or flags.
    // shortcut: imports are host-only, Secure, root-path session cookies; replace when the server cookie attributes are verified.
    fun parse(url: HttpUrl, cookieHeader: String?): List<Cookie> {
        if (!url.isHttps || cookieHeader.isNullOrBlank()) return emptyList()
        val pairs = cookieHeader.split(';').mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            pair.substring(0, separator).trim() to pair.substring(separator + 1).trim()
        }
        val ambiguousNames = pairs.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        return pairs.filterNot { it.first in ambiguousNames }.mapNotNull { (name, value) ->
            runCatching {
                Cookie.Builder().name(name).value(value).hostOnlyDomain(url.host).path("/").secure().build()
            }.getOrNull()
        }
    }
}
