package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoodleAjaxClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MoodleAjaxClient
    private val userAgent = "UniPortal/test (Android; read-only)"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val jar = object : CookieJar {
            private var cookies = listOf(
                Cookie.Builder().name("MoodleSession").value("XXXX").hostOnlyDomain(server.hostName).build(),
            )
            override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this.cookies = cookies
            }
        }
        client = MoodleAjaxClient(jar, userAgent, server.url("/"), requestDelayMillis = 0)
        AppLog.clear()
    }

    @After
    fun stop() {
        server.close()
        AppLog.clear()
    }

    @Test
    fun `saved cookies reach dashboard and exact read-only AJAX request with refreshed response cookies`() {
        server.enqueue(MockResponse(
            headers = headersOf("Content-Type", "text/html", "Set-Cookie", "MoodleSession=XXXX2; Path=/"),
            body = fixture("moodle_config_ok.html"),
        ))
        enqueueJson("courses_ok.json")

        val page = client.fetchFirstCoursePage().getOrThrow()
        assertEquals(21, page.courses.size)
        assertEquals(21, page.nextOffset)
        val dashboard = server.takeRequest()
        assertEquals("GET", dashboard.method)
        assertEquals("/", dashboard.url.encodedPath)
        assertEquals("MoodleSession=XXXX", dashboard.headers["Cookie"])
        val ajax = server.takeRequest()
        assertEquals("POST", ajax.method)
        assertEquals("/lib/ajax/service.php", ajax.url.encodedPath)
        assertEquals("XXXX", ajax.url.queryParameter("sesskey"))
        assertEquals("core_course_get_enrolled_courses_by_timeline_classification", ajax.url.queryParameter("info"))
        assertEquals("MoodleSession=XXXX2", ajax.headers["Cookie"])
        assertTrue(ajax.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals(Json.parseToJsonElement(
            """[{"index":0,"methodname":"core_course_get_enrolled_courses_by_timeline_classification","args":{"offset":0,"limit":24,"classification":"all","sort":"ul.timeaccess desc","customfieldname":"","customfieldvalue":""}}]""",
        ), Json.parseToJsonElement(ajax.body!!.utf8()))
        assertEquals(userAgent, dashboard.headers["User-Agent"])
        assertEquals(userAgent, ajax.headers["User-Agent"])
        assertTrue(AppLog.snapshot().isEmpty())
    }

    @Test
    fun `empty courses are a valid first page`() {
        enqueueConfig()
        enqueueJson("courses_empty_synthetic.json")
        assertTrue(client.fetchFirstCoursePage().getOrThrow().courses.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `invalidsesskey and servicerequireslogin refresh once then succeed`() {
        listOf("courses_invalid_sesskey_synthetic.json", "courses_expired_session_synthetic.json").forEach { error ->
            enqueueConfig()
            enqueueJson(error)
            enqueueConfig("NEXT")
            enqueueJson("courses_ok.json")
            assertTrue(client.fetchFirstCoursePage().isSuccess)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("XXXX", server.takeRequest().url.queryParameter("sesskey"))
            assertEquals("GET", server.takeRequest().method)
            assertEquals("NEXT", server.takeRequest().url.queryParameter("sesskey"))
        }
        assertEquals(8, server.requestCount)
    }

    @Test
    fun `second session failure needs login and never starts a third attempt`() {
        listOf("courses_invalid_sesskey_synthetic.json", "courses_expired_session_synthetic.json").forEach { error ->
            repeat(2) {
                enqueueConfig()
                enqueueJson(error)
            }
            assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
            repeat(4) { server.takeRequest() }
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
        assertEquals(8, server.requestCount)
    }

    @Test
    fun `HTML instead of JSON expires session regardless of advertised content type`() {
        listOf("text/html", "application/json", "text/plain", "application/xhtml+xml").forEach { type ->
            enqueueConfig()
            server.enqueue(MockResponse(headers = headersOf("Content-Type", type), body = fixture("courses_login_page_synthetic.html")))
            assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
            repeat(2) { server.takeRequest() }
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `login HTML during key refresh stops before another AJAX request`() {
        enqueueConfig()
        enqueueJson("courses_invalid_sesskey_synthetic.json")
        server.enqueue(MockResponse(body = fixture("courses_login_page_synthetic.html")))
        assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `dashboard redirect to SSO is blocked before any off-origin request`() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "https://sso1.ut.ac.ir/login")))
        assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `AJAX off-origin redirect is blocked without forwarding sesskey or cookies`() {
        MockWebServer().use { other ->
            other.start()
            enqueueConfig()
            server.enqueue(MockResponse(code = 307, headers = headersOf("Location", other.url("/capture").toString())))
            assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
            assertEquals(2, server.requestCount)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun `same-origin dashboard redirects are followed`() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "/my/")))
        enqueueConfig()
        enqueueJson("courses_ok.json")
        assertTrue(client.fetchFirstCoursePage().isSuccess)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `server errors malformed JSON and wrong media types fail safely without retry`() {
        listOf(
            MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture("courses_error_object_synthetic.json")) to Reason.SERVER_ERROR,
            MockResponse(headers = headersOf("Content-Type", "application/json"), body = "{private-example-input") to Reason.PARSE_ERROR,
            MockResponse(headers = headersOf("Content-Type", "image/png"), body = "private-example-input") to Reason.PARSE_ERROR,
            MockResponse(code = 500, body = "private-example-input") to Reason.SERVER_ERROR,
        ).forEach { (response, reason) ->
            enqueueConfig()
            server.enqueue(response)
            assertReason(reason, client.fetchFirstCoursePage())
            repeat(2) { server.takeRequest() }
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `HTTP authentication failure immediately needs login`() {
        listOf(401, 403).forEach { code ->
            server.enqueue(MockResponse(code = code))
            assertReason(Reason.SESSION_EXPIRED, client.fetchFirstCoursePage())
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `network failure becomes a safe error without connection retry`() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertReason(Reason.NETWORK_ERROR, client.fetchFirstCoursePage())
    }

    @Test
    fun `cookie storage failures never expose their original exception`() {
        val brokenJar = object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = throw IllegalStateException("private-example-input")
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        val broken = MoodleAjaxClient(brokenJar, userAgent, server.url("/"), 0)
        assertReason(Reason.STORAGE_ERROR, broken.fetchFirstCoursePage())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `oversized response is rejected before parsing`() {
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "text/html"), body = "x".repeat(8 * 1024 * 1024 + 1)))
        assertReason(Reason.PARSE_ERROR, client.fetchFirstCoursePage())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent loads are serialized and HTTP requests are spaced`() {
        val paced = MoodleAjaxClient(CookieJar.NO_COOKIES, userAgent, server.url("/"), requestDelayMillis = 60)
        repeat(2) {
            enqueueConfig()
            enqueueJson("courses_ok.json")
        }
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = List(2) { executor.submit<Boolean> {
                start.await()
                paced.fetchFirstCoursePage().isSuccess
            } }
            val before = System.nanoTime()
            start.countDown()
            assertTrue(futures.all { it.get(5, TimeUnit.SECONDS) })
            assertTrue(System.nanoTime() - before >= TimeUnit.MILLISECONDS.toNanos(180))
            assertEquals(listOf("GET", "POST", "GET", "POST"), List(4) { server.takeRequest().method })
        } finally {
            executor.shutdownNow()
        }
    }

    private fun enqueueConfig(key: String = "XXXX") {
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "text/html"), body = fixture("moodle_config_ok.html").replace("XXXX", key)))
    }

    private fun enqueueJson(name: String) {
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()

    private fun assertReason(reason: Reason, result: Result<*>) {
        val failure = result.exceptionOrNull()
        assertTrue(failure is PortalException)
        assertEquals(reason, (failure as PortalException).reason)
        assertNull(failure.cause)
        assertFalse(failure.stackTraceToString().contains("private-example-input"))
        assertTrue(AppLog.snapshot().isEmpty())
    }
}
