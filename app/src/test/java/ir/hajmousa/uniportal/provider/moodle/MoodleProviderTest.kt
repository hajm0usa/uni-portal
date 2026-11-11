package ir.hajmousa.uniportal.provider.moodle

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.ProviderSession.State
import ir.hajmousa.uniportal.data.AgendaItem
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
        assertEquals(listOf(provider.courses, provider.agendaPreview), provider.sources)
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

    @Test
    fun `registered agenda preview returns shared deadlines and updates only its provider session`() = runBlocking {
        val other = newProvider()
        enqueuePage("calendar_ok.json")
        val item = provider.agendaPreview.fetch().getOrThrow().single()
        assertEquals(AgendaItem.Kind.DEADLINE, item.kind)
        assertEquals(provider.id, item.providerId)
        assertEquals(State.Active, provider.session.state.value)
        assertEquals(State.Unknown, other.session.state.value)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `agenda bounds follow Tehran local midnight rather than device or UTC day`() = runBlocking {
        val zone = ZoneId.of("Asia/Tehran")
        listOf(
            "2026-11-10T20:29:59Z" to "2026-11-10",
            "2026-11-10T20:30:00Z" to "2026-11-11",
            "2021-03-21T12:00:00Z" to "2021-03-21",
        ).forEach { (instant, date) ->
            val clock = Clock.fixed(Instant.parse(instant), ZoneOffset.ofHours(-8))
            val fixed = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0), clock)
            enqueuePage("calendar_empty_synthetic.json")
            assertTrue(fixed.agendaPreview.fetch().getOrThrow().isEmpty())
            server.takeRequest()
            val args = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray.single()
                .jsonObject.getValue("args").jsonObject
            val day = java.time.LocalDate.parse(date)
            assertEquals(day.atStartOfDay(zone).toEpochSecond(), args.getValue("timesortfrom").jsonPrimitive.long)
            assertEquals(day.plusDays(7).atStartOfDay(zone).toEpochSecond(), args.getValue("timesortto").jsonPrimitive.long)
        }
    }

    @Test
    fun `calendar expiry requires login and successful empty preview recovers without altering course source`() = runBlocking {
        enqueuePage("courses_ok.json")
        assertEquals(21, provider.courses.fetch().getOrThrow().size)
        repeat(2) { enqueuePage("calendar_expired_session_synthetic.json") }
        val failure = provider.agendaPreview.fetch().exceptionOrNull() as PortalException
        assertEquals(PortalException.Reason.SESSION_EXPIRED, failure.reason)
        assertEquals(State.NeedsLogin, provider.session.state.value)
        server.enqueue(MockResponse(code = 500))
        assertTrue(provider.agendaPreview.fetch().isFailure)
        assertEquals(State.NeedsLogin, provider.session.state.value)
        enqueuePage("calendar_empty_synthetic.json")
        assertTrue(provider.agendaPreview.fetch().getOrThrow().isEmpty())
        assertEquals(State.Active, provider.session.state.value)
        enqueuePage("courses_ok.json")
        assertEquals(21, provider.courses.fetch().getOrThrow().size)
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
}
