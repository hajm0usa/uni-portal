package ir.hajmousa.uniportal.auth

import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
internal data class CookieRecord(val url: String, val value: String)

internal data class LoadedCookies(val records: List<CookieRecord>, val cookies: List<Cookie>)

internal object CookieJarLogic {
    fun save(records: List<CookieRecord>, url: HttpUrl, incoming: List<Cookie>, now: Long): List<CookieRecord> {
        val identities = incoming.map { listOf(it.name, it.domain, it.path) }.toSet()
        val retained = records.filterNot { record ->
            if (record.url.toHttpUrlOrNull()?.host != url.host) return@filterNot false
            val existing = Cookie.parse(record.url.toHttpUrl(), record.value) ?: return@filterNot true
            listOf(existing.name, existing.domain, existing.path) in identities
        }
        return retained + incoming.filter { it.expiresAt > now }.map { CookieRecord(url.toString(), it.toString()) }
    }

    fun load(records: List<CookieRecord>, requestUrl: HttpUrl, now: Long): LoadedCookies {
        val parsed = records.mapNotNull { record ->
            val sourceUrl = record.url.toHttpUrlOrNull() ?: return@mapNotNull null
            Cookie.parse(sourceUrl, record.value)?.let { record to it }
        }
        val retained = parsed.filter { it.second.expiresAt > now }
        return LoadedCookies(retained.map { it.first }, retained.map { it.second }.filter { it.matches(requestUrl) })
    }

    fun clear(records: List<CookieRecord>, host: String): List<CookieRecord> =
        records.filterNot { it.url.toHttpUrlOrNull()?.host == host.lowercase() }

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { toHttpUrl() }.getOrNull()
}
