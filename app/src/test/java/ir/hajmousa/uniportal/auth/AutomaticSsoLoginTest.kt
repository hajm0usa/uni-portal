package ir.hajmousa.uniportal.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AutomaticSsoLoginTest {
    private val function = "function(credentials) { return credentials; }"
    private val credentials = LoginCredentials("synthetic-user", "synthetic-password")
    private val url = "https://sso1.ut.ac.ir/ApiContainer.SSO.RCL1/Account/login"

    @Test
    fun `credentials are released only once and only to the exact secure SSO origin`() {
        val login = AutomaticSsoLogin(function)
        listOf("http://sso1.ut.ac.ir/", "https://sso1.ut.ac.ir.evil.invalid/", "https://elearn.ut.ac.ir/",
            "https://sso1.ut.ac.ir:444/", "https://user@sso1.ut.ac.ir/", "file:///login", "not a url").forEach {
            assertNull(login.probe(it))
            assertNull(login.submit(it, credentials))
        }
        assertNotNull(login.probe(url))
        assertNotNull(login.submit(url, credentials))
        assertNull(login.probe(url))
        assertNull(login.submit(url, credentials))
        assertNull(AutomaticSsoLogin(function, attempted = true).submit(url, credentials))
        val stopped = AutomaticSsoLogin(function)
        stopped.stop()
        assertNull(stopped.probe(url))
        assertNull(stopped.submit(url, credentials))
    }

    @Test
    fun `probing never contains credentials and JavaScript arguments use safe JSON escaping`() {
        val login = AutomaticSsoLogin(function)
        assertEquals("($function)(null)", login.probe(url))
        val tricky = LoginCredentials("synthetic-user", "\");globalThis.compromised=true;//\n\\\u2028\u2029")
        val script = login.submit(url, tricky)!!
        val argument = script.removePrefix("($function)(").removeSuffix(")")
        val decoded = Json.decodeFromString<LoginCredentials>(argument)
        assertEquals(tricky.password, decoded.password)
        assertFalse(script.contains('\u2028'))
        assertFalse(script.contains('\u2029'))
    }
}
