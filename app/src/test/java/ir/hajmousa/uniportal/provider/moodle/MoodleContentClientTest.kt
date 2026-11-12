package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.ProviderSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoodleContentClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MoodleAjaxClient

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val jar = object : CookieJar {
            private val cookie = Cookie.Builder().name("MoodleSession").value("XXXX").hostOnlyDomain(server.hostName).build()
            override fun loadForRequest(url: HttpUrl) = listOf(cookie).filter { it.matches(url) }
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        }
        client = MoodleAjaxClient(jar, "UniPortal/test (Android; read-only)", server.url("/"), 0)
        AppLog.clear()
    }

    @After
    fun stop() { server.close(); AppLog.clear() }

    @Test
    fun `course and folder use only observed GET routes and cookies without sesskey query or form submission`() {
        enqueuePage("course_content_ok.html")
        assertEquals(4, client.fetchCourseContent("1").getOrThrow().size)
        enqueuePage("folder_ok.html")
        assertEquals(2, client.fetchFolderFiles("1", "205").getOrThrow().size)
        val requests = List(4) { server.takeRequest() }
        assertEquals(listOf("/", "/course/view.php", "/", "/mod/folder/view.php"), requests.map { it.url.encodedPath })
        assertEquals("1", requests[1].url.queryParameter("id"))
        assertEquals("205", requests[3].url.queryParameter("id"))
        assertEquals(setOf("id"), requests[1].url.queryParameterNames)
        assertEquals(setOf("id"), requests[3].url.queryParameterNames)
        requests.forEach {
            assertEquals("GET", it.method)
            assertEquals("MoodleSession=XXXX", it.headers["Cookie"])
            assertEquals("UniPortal/test (Android; read-only)", it.headers["User-Agent"])
            assertNull(it.url.queryParameter("sesskey"))
        }
        assertEquals(4, server.requestCount)
        assertTrue(AppLog.snapshot().isEmpty())
    }

    @Test
    fun `invalid and injection-like ids fail safely before any request`() {
        listOf("", "0", "-1", "+1", "01", " 1", "1&sesskey=XXXX", "../1", "https://evil.invalid/", "999999999999999999999999").forEach { id ->
            assertReason(Reason.PARSE_ERROR, client.fetchCourseContent(id))
            assertReason(Reason.PARSE_ERROR, client.fetchFolderFiles(id, "205"))
            assertReason(Reason.PARSE_ERROR, client.fetchFolderFiles("1", id))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `password HTML refreshes the native session once and retries only the selected page`() {
        enqueuePage("content_login_synthetic.html")
        enqueuePage("course_content_ok.html")
        assertEquals(4, client.fetchCourseContent("1").getOrThrow().size)
        assertEquals(listOf("/", "/course/view.php", "/", "/course/view.php"), List(4) { server.takeRequest().url.encodedPath })
        repeat(2) { enqueuePage("content_login_synthetic.html") }
        assertReason(Reason.SESSION_EXPIRED, client.fetchFolderFiles("1", "205"))
        assertEquals(listOf("/", "/mod/folder/view.php", "/", "/mod/folder/view.php"), List(4) { server.takeRequest().url.encodedPath })
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        assertEquals(8, server.requestCount)
    }

    @Test
    fun `ordinary error malformed and wrong-course pages fail safely without retries`() {
        listOf(
            MockResponse(headers = headersOf("Content-Type", "text/html"), body = fixture("content_error_synthetic.html")) to Reason.PARSE_ERROR,
            MockResponse(body = "private-example-input") to Reason.PARSE_ERROR,
            MockResponse(code = 500, body = "private-example-input") to Reason.SERVER_ERROR,
            MockResponse(code = 401) to Reason.SESSION_EXPIRED,
        ).forEach { (response, reason) ->
            enqueueHtml("moodle_config_ok.html")
            server.enqueue(response)
            assertReason(reason, client.fetchCourseContent("1"))
            repeat(2) { server.takeRequest() }
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
        enqueuePage("course_content_ok.html")
        assertReason(Reason.PARSE_ERROR, client.fetchCourseContent("2"))
        assertEquals(10, server.requestCount)
    }

    @Test
    fun `content redirect is rejected before cookies or query values reach a different origin`() {
        MockWebServer().use { other ->
            other.start()
            enqueueHtml("moodle_config_ok.html")
            server.enqueue(MockResponse(code = 307, headers = headersOf("Location", other.url("/capture").toString())))
            assertReason(Reason.SESSION_EXPIRED, client.fetchCourseContent("1"))
            assertEquals(2, server.requestCount)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun `synthetic empty content and folder trees succeed without following files or download-all form`() {
        enqueuePage("course_content_empty_synthetic.html")
        assertTrue(client.fetchCourseContent("1").getOrThrow().isEmpty())
        enqueuePage("folder_empty_synthetic.html")
        assertTrue(client.fetchFolderFiles("1", "205").getOrThrow().isEmpty())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `scoped provider sources share session state while another provider remains untouched`() = runBlocking {
        val provider = MoodleProvider(client)
        val other = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0))
        val content = provider.courseContent("1")
        val folder = provider.folderFiles("1", "205")
        assertEquals(0, server.requestCount)
        assertEquals(listOf(provider.courses, provider.agendaPreview), provider.sources)
        enqueuePage("course_content_ok.html")
        assertEquals(4, content.fetch().getOrThrow().size)
        assertEquals(ProviderSession.State.Active, provider.session.state.value)
        assertEquals(ProviderSession.State.Unknown, other.session.state.value)
        repeat(2) { enqueuePage("content_login_synthetic.html") }
        assertReason(Reason.SESSION_EXPIRED, folder.fetch())
        assertEquals(ProviderSession.State.NeedsLogin, provider.session.state.value)
        assertEquals(ProviderSession.State.Unknown, other.session.state.value)
        enqueuePage("folder_ok.html")
        assertTrue(folder.fetch().getOrThrow().all { it.providerId == provider.id })
        assertEquals(ProviderSession.State.Active, provider.session.state.value)
        assertEquals(8, server.requestCount)
    }

    private fun enqueuePage(name: String) { enqueueHtml("moodle_config_ok.html"); enqueueHtml(name) }
    private fun enqueueHtml(name: String) {
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "text/html; charset=utf-8"), body = fixture(name)))
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
        .replace("https://example.invalid", "https://elearn.ut.ac.ir")
    private fun assertReason(reason: Reason, result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue(error is PortalException)
        assertEquals(reason, (error as PortalException).reason)
        assertNull(error.cause)
        assertFalse(error.stackTraceToString().contains("private-example-input"))
        assertTrue(AppLog.snapshot().isEmpty())
    }
}
