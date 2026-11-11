package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncCoordinatorTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: MoodleProvider
    private lateinit var sync: SyncCoordinator
    private val dao = MemoryCourseDao()
    private val agendaDao = MemoryAgendaDao(dao)
    private val now = 1700000000L
    private val clock = Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC)

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        provider = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0), clock)
        sync = coordinator()
    }
    private fun coordinator() = SyncCoordinator(provider, CourseRepository(provider, dao, clock), AgendaRepository(provider, agendaDao, clock), clock)
    @After
    fun stop() { server.close() }

    @Test
    fun `cached restores both sections and recent empty snapshots avoid all networking`() = runBlocking {
        dao.replacePage("moodle", emptyList(), now)
        agendaDao.replacePreview("moodle", emptyList(), now)
        assertNotNull(sync.cached().agenda.getOrThrow())
        val result = sync.syncIfStale()
        assertTrue(result.courses.getOrThrow()!!.courses.isEmpty())
        assertTrue(result.agenda.getOrThrow()!!.items.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `each section refreshes only when stale and forced refresh bypasses freshness`() = runBlocking {
        dao.replacePage("moodle", emptyList(), now)
        agendaDao.replacePreview("moodle", emptyList(), now - 1800)
        enqueuePage("calendar_ok.json")
        val result = sync.syncIfStale()
        assertTrue(result.courses.getOrThrow()!!.courses.isEmpty())
        assertEquals(1, result.agenda.getOrThrow()!!.items.size)
        assertEquals(2, server.requestCount)
        enqueuePage("courses_ok.json")
        enqueuePage("calendar_ok.json")
        val forced = sync.syncIfStale(force = true)
        assertEquals(21, forced.courses.getOrThrow()!!.courses.size)
        assertEquals(1, forced.agenda.getOrThrow()!!.items.size)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `ordinary section failure never prevents the other section refreshing`() = runBlocking {
        server.enqueue(MockResponse(code = 500))
        enqueuePage("calendar_ok.json")
        val result = sync.syncIfStale()
        assertTrue(result.courses.isFailure)
        assertEquals(1, result.agenda.getOrThrow()!!.items.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `shared session expiry skips the other section and subsequent foreground checks do not loop`() = runBlocking {
        server.enqueue(MockResponse(body = "<html>Synthetic login page</html>"))
        val result = sync.syncIfStale()
        assertTrue(result.courses.isFailure)
        assertEquals(PortalException.Reason.SESSION_EXPIRED, (result.agenda.exceptionOrNull() as PortalException).reason)
        assertEquals(ProviderSession.State.NeedsLogin, sync.session.value)
        sync.syncIfStale(force = true)
        assertEquals(1, server.requestCount)
        sync.clearForAccountChange().getOrThrow()
        enqueuePage("courses_ok.json")
        enqueuePage("calendar_ok.json")
        assertTrue(sync.syncIfStale(force = true).agenda.isSuccess)
        assertEquals(ProviderSession.State.Active, sync.session.value)
    }

    @Test
    fun `concurrent coordinators for one provider deduplicate stale refresh after acquiring the lock`() {
        enqueuePage("courses_ok.json")
        enqueuePage("calendar_ok.json")
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val jobs = listOf(sync, coordinator()).map { coordinator -> pool.submit<Boolean> {
                start.await()
                runBlocking { coordinator.syncIfStale().agenda.isSuccess }
            } }
            start.countDown()
            assertTrue(jobs.all { it.get(5, TimeUnit.SECONDS) })
            assertEquals(4, server.requestCount)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `account clearing serializes with the whole multi-section sync`() {
        server.enqueue(MockResponse.Builder().body(fixture("moodle_config_ok.html")).bodyDelay(100, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture("courses_ok.json")))
        enqueuePage("calendar_ok.json")
        val pool = Executors.newFixedThreadPool(2)
        try {
            val load = pool.submit<Boolean> { runBlocking { sync.syncIfStale().agenda.isSuccess } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val clear = pool.submit<Boolean> { runBlocking { coordinator().clearForAccountChange().isSuccess } }
            assertTrue(load.get(5, TimeUnit.SECONDS))
            assertTrue(clear.get(5, TimeUnit.SECONDS))
            assertNull(dao.readPage("moodle"))
            assertNull(agendaDao.readPreview("moodle"))
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `freshness includes exact threshold absent data and clock rollback`() {
        assertTrue(SyncCoordinator.isStale(null, now))
        assertFalse(SyncCoordinator.isStale(now - 1799, now))
        assertTrue(SyncCoordinator.isStale(now - 1800, now))
        assertTrue(SyncCoordinator.isStale(now + 1, now))
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
}
