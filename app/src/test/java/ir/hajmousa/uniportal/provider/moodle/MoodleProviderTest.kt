package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.ProviderSession.State
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoodleProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: MoodleProvider

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        provider = newProvider()
    }

    private fun newProvider() = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0))

    @After
    fun stop() { server.close() }

    @Test
    fun `registered course source returns shared models and proves only its own session active`() = runBlocking {
        val other = newProvider()
        assertEquals("moodle", provider.id)
        assertEquals(State.Unknown, provider.session.state.value)
        assertEquals(0, server.requestCount)
        assertEquals(listOf(provider.courses), provider.sources)
        enqueuePage("courses_ok.json")
        val courses = provider.courses.fetch().getOrThrow()
        assertEquals(21, courses.size)
        assertTrue(courses.all { it.providerId == provider.id })
        assertEquals(State.Active, provider.session.state.value)
        assertEquals(State.Unknown, other.session.state.value)
    }

    @Test
    fun `bounded session retry requires login only for the failing provider then successful fetch recovers`() = runBlocking {
        val other = newProvider()
        enqueuePage("courses_ok.json")
        other.courses.fetch().getOrThrow()
        repeat(2) { enqueuePage("courses_expired_session_synthetic.json") }
        val failure = provider.courses.fetch().exceptionOrNull() as PortalException
        assertEquals(PortalException.Reason.SESSION_EXPIRED, failure.reason)
        assertEquals(State.NeedsLogin, provider.session.state.value)
        assertEquals(State.Active, other.session.state.value)
        assertEquals(6, server.requestCount)
        enqueuePage("courses_empty_synthetic.json")
        assertTrue(provider.courses.fetch().getOrThrow().isEmpty())
        assertEquals(State.Active, provider.session.state.value)
    }

    @Test
    fun `server failure never proves an unknown session active or clears a login requirement`() = runBlocking {
        server.enqueue(MockResponse(code = 500))
        assertTrue(provider.courses.fetch().isFailure)
        assertEquals(State.Unknown, provider.session.state.value)
        server.enqueue(MockResponse(body = fixture("courses_login_page_synthetic.html")))
        assertTrue(provider.courses.fetch().isFailure)
        assertEquals(State.NeedsLogin, provider.session.state.value)
        server.enqueue(MockResponse(code = 500))
        assertTrue(provider.courses.fetch().isFailure)
        assertEquals(State.NeedsLogin, provider.session.state.value)
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
}
