package ir.hajmousa.uniportal.auth

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieSecurityTest {
    @Test
    fun `browser cookies are host-only Secure session cookies`() {
        val url = "https://sso1.ut.ac.ir/login".toHttpUrl()
        val cookies = WebViewCookieParser.parse(url, "__arcsjs=opaque==; __arcsjsc=other; broken")

        assertEquals(listOf("__arcsjs", "__arcsjsc"), cookies.map { it.name })
        assertEquals("opaque==", cookies.first().value)
        assertTrue(cookies.all { it.hostOnly && it.secure && !it.persistent && it.path == "/" })
        assertTrue(cookies.all { it.matches("https://sso1.ut.ac.ir/lib/ajax/service.php".toHttpUrl()) })
        assertFalse(cookies.any { it.matches("https://elearn.ut.ac.ir/lib/ajax/service.php".toHttpUrl()) })
    }

    @Test
    fun `browser cookies reject plaintext origins and ambiguous duplicate names`() {
        assertTrue(WebViewCookieParser.parse("http://sso1.ut.ac.ir".toHttpUrl(), "sid=secret").isEmpty())
        assertTrue(WebViewCookieParser.parse("https://sso1.ut.ac.ir".toHttpUrl(), "sid=old; sid=new").isEmpty())
        assertTrue(WebViewCookieParser.parse("https://sso1.ut.ac.ir".toHttpUrl(), "=invalid; sid=ok").size == 1)
        assertTrue(WebViewCookieParser.parse("https://sso1.ut.ac.ir".toHttpUrl(), null).isEmpty())
    }

    @Test
    fun `AES GCM round trip authenticates data and leaves no plaintext in stored bytes`() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val plaintext = "session-cookie-value".encodeToByteArray()
        val encrypted = AesGcm.seal(key, plaintext)
        assertArrayEquals(plaintext, AesGcm.open(key, encrypted))
        assertFalse(Base64.getEncoder().encodeToString(encrypted.ciphertext).contains("session-cookie-value"))
        assertNotEquals(encrypted.iv.toList(), AesGcm.seal(key, plaintext).iv.toList())

        val tampered = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        try {
            AesGcm.open(key, SealedData(encrypted.iv, tampered))
            throw AssertionError("Modified ciphertext must fail authentication")
        } catch (_: AEADBadTagException) {
            // Expected: AES-GCM rejects modified ciphertext.
        }
    }

    @Test
    fun `cookie records are updated by host and identity without deleting sibling hosts`() {
        val sso = "https://sso1.ut.ac.ir/".toHttpUrl()
        val moodle = "https://elearn.ut.ac.ir/".toHttpUrl()
        val now = System.currentTimeMillis()
        val oldSso = Cookie.parse(sso, "sid=old; Path=/; Secure")!!
        val sibling = Cookie.parse(moodle, "MoodleSession=kept; Path=/; Secure")!!
        val initial = CookieJarLogic.save(emptyList(), sso, listOf(oldSso), now) +
            CookieJarLogic.save(emptyList(), moodle, listOf(sibling), now)
        val freshSso = Cookie.parse(sso, "sid=new; Path=/; Secure")!!

        val updated = CookieJarLogic.save(initial, sso, listOf(freshSso), now)
        val loadedSso = CookieJarLogic.load(updated, sso, now)
        val loadedMoodle = CookieJarLogic.load(updated, moodle, now)
        assertEquals(listOf("new"), loadedSso.cookies.map { it.value })
        assertEquals(listOf("kept"), loadedMoodle.cookies.map { it.value })
        assertEquals(2, updated.size)
    }

    @Test
    fun `cookie domain path secure expiry and explicit host clearing are honored`() {
        val sso = "https://sso1.example.com/login".toHttpUrl()
        val moodle = "https://elearn.example.com/lib/ajax/service.php".toHttpUrl()
        val nested = "https://elearn.example.com/admin/".toHttpUrl()
        val http = "http://elearn.example.com/lib/ajax/service.php".toHttpUrl()
        val now = System.currentTimeMillis()
        val shared = Cookie.Builder().name("shared").value("x").domain("example.com")
            .path("/lib").secure().httpOnly().build()
        val scoped = Cookie.Builder().name("narrow").value("x").domain("example.com")
            .path("/admin").secure().build()
        val expired = Cookie.Builder().name("old").value("x").hostOnlyDomain("elearn.example.com")
            .path("/").expiresAt(now - 1).build()
        val moodleCookie = Cookie.parse(moodle, "MoodleSession=m; Path=/; Secure")!!
        val records = CookieJarLogic.save(emptyList(), sso, listOf(shared, scoped, expired), now) +
            CookieJarLogic.save(emptyList(), moodle, listOf(moodleCookie), now)

        assertEquals(listOf("MoodleSession", "shared"), CookieJarLogic.load(records, moodle, now).cookies.map { it.name }.sorted())
        assertEquals(listOf("MoodleSession", "narrow"), CookieJarLogic.load(records, nested, now).cookies.map { it.name }.sorted())
        assertTrue(CookieJarLogic.load(records, http, now).cookies.isEmpty())
        assertEquals(3, CookieJarLogic.load(records, moodle, now).records.size)
        assertEquals(listOf("MoodleSession"), CookieJarLogic.clear(records, "sso1.example.com").map {
            Cookie.parse(it.url.toHttpUrl(), it.value)!!.name
        })
        assertEquals(listOf("narrow", "shared"), CookieJarLogic.clear(records, "elearn.example.com").map {
            Cookie.parse(it.url.toHttpUrl(), it.value)!!.name
        }.sorted())
    }
}
