package ir.hajmousa.uniportal.auth

import java.net.URI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class AutomaticSsoLogin(private val script: String, private var attempted: Boolean = false) {
    fun probe(url: String): String? = if (!attempted && isSsoOrigin(url)) "($script)(null)" else null

    fun stop() { attempted = true }

    fun submit(url: String, credentials: LoginCredentials): String? {
        if (attempted || !isSsoOrigin(url)) return null
        attempted = true
        val json = Json.encodeToString(credentials).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        return "($script)($json)"
    }

    private fun isSsoOrigin(url: String) = runCatching { SsoHosts.isSsoOrigin(URI(url)) }.getOrDefault(false)
}
