package ir.hajmousa.uniportal.auth

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsoHostsTest {
    @Test
    fun `only the two observed HTTPS hosts and standard ports are in the WebView allow-list`() {
        listOf(
            "https://elearn.ut.ac.ir/",
            "https://sso1.ut.ac.ir/login",
            "HTTPS://ELEARN.UT.AC.IR/admin/oauth2callback.php?code=private",
            "https://elearn.ut.ac.ir:443/",
        ).forEach { assertTrue(SsoHosts.isAllowed(URI(it))) }
        listOf(
            "http://elearn.ut.ac.ir/",
            "https://attacker-elearn.ut.ac.ir/",
            "https://elearn.ut.ac.ir.attacker.invalid/",
            "https://ut.ac.ir/",
            "https://www.ut.ac.ir/",
            "https://elearn.ut.ac.ir:8443/",
            "https://user@elearn.ut.ac.ir/",
            "file:///etc/passwd",
            "javascript:alert(1)",
        ).forEach { assertFalse(SsoHosts.isAllowed(URI(it))) }
    }

    @Test
    fun `callback is recognized by exact host and path without looking at query values`() {
        assertTrue(SsoHosts.isCallback(URI("https://elearn.ut.ac.ir/admin/oauth2callback.php?code=private&state=private")))
        assertFalse(SsoHosts.isCallback(URI("https://elearn.ut.ac.ir/admin/oauth2callback.php/extra")))
        assertFalse(SsoHosts.isCallback(URI("https://sso1.ut.ac.ir/admin/oauth2callback.php")))
        assertFalse(SsoHosts.isCallback(URI("https://elearn.ut.ac.ir/course/view.php")))
    }

    @Test
    fun `only ordinary HTTP URLs can leave the WebView for the system browser`() {
        listOf("https://example.invalid/path", "http://example.invalid/path")
            .forEach { assertTrue(SsoHosts.isExternalBrowserUrl(URI(it))) }
        listOf("javascript:alert(1)", "file:///tmp/example", "intent://example.invalid/#Intent;end", "https://user@example.invalid/")
            .forEach { assertFalse(SsoHosts.isExternalBrowserUrl(URI(it))) }
    }
}
