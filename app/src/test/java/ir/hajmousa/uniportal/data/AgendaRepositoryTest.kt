package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

class AgendaRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: MoodleProvider
    private lateinit var repository: AgendaRepository
    private val courses = MemoryCourseDao()
    private val dao = MemoryAgendaDao(courses)
    private val clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC)
    private val old = AgendaItem("moodle", "99", "Synthetic deadline", 1700006400, AgendaItem.Kind.DEADLINE)

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        provider = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0), clock)
        repository = AgendaRepository(provider, dao, clock)
        AppLog.clear()
    }

    @After
    fun stop() { server.close(); AppLog.clear() }

    @Test
    fun `successful preview replaces only its provider and is restored without networking`() = runBlocking {
        dao.replacePreview("moodle", listOf(old), 1)
        val sibling = old.copy(providerId = "other")
        dao.replacePreview("other", listOf(sibling), 2)
        enqueuePage("calendar_ok.json")
        val preview = repository.fetchPreview().getOrThrow()
        assertEquals("101", preview.items.single().remoteId)
        assertEquals(1700000000L, preview.lastSuccessEpochSeconds)
        assertNull(preview.lastError)
        assertEquals(ProviderSession.State.Active, repository.session.value)
        assertEquals(preview, AgendaRepository(provider, dao, clock).cachedPreview().getOrThrow())
        assertEquals(listOf(sibling), dao.readPreview("other")!!.items)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `failure preserves previous preview and timestamp and records only safe section error`() = runBlocking {
        dao.replacePreview("moodle", listOf(old), 123)
        courses.replacePage("moodle", listOf(Course("moodle", "1", "Sample", null)), 10)
        server.enqueue(MockResponse(code = 500, body = "private-example-input"))
        assertReason(Reason.SERVER_ERROR, repository.fetchPreview())
        val preview = repository.cachedPreview().getOrThrow()!!
        assertEquals(listOf(old), preview.items)
        assertEquals(123L, preview.lastSuccessEpochSeconds)
        assertEquals(Reason.SERVER_ERROR, preview.lastError)
        assertNull(courses.readPage("moodle")!!.lastError)
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
    }

    @Test
    fun `successful empty preview has a persisted snapshot unlike absent cache`() = runBlocking {
        assertNull(repository.cachedPreview().getOrThrow())
        dao.replacePreview("moodle", listOf(old), 1)
        enqueuePage("calendar_empty_synthetic.json")
        val preview = repository.fetchPreview().getOrThrow()
        assertTrue(preview.items.isEmpty())
        assertEquals(1700000000L, preview.lastSuccessEpochSeconds)
        assertEquals(preview, repository.cachedPreview().getOrThrow())
    }

    @Test
    fun `expiry survives repository recreation even without a successful snapshot`() = runBlocking {
        repeat(2) { enqueuePage("calendar_expired_session_synthetic.json") }
        assertReason(Reason.SESSION_EXPIRED, repository.fetchPreview())
        assertEquals(ProviderSession.State.NeedsLogin, repository.session.value)
        val fresh = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0))
        val restored = AgendaRepository(fresh, dao, clock)
        assertNull(restored.cachedPreview().getOrThrow())
        assertEquals(ProviderSession.State.NeedsLogin, restored.session.value)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `account transition clears courses and agenda metadata but not another provider`() = runBlocking {
        dao.replacePreview("moodle", listOf(old), 1)
        dao.recordFailure("moodle", Reason.SESSION_EXPIRED)
        dao.replacePreview("other", listOf(old.copy(providerId = "other")), 2)
        courses.replacePage("moodle", emptyList(), 3)
        val courseRepository = CourseRepository(provider, courses, clock)
        repository.cachedPreview().getOrThrow()
        courseRepository.clearCache().getOrThrow()
        assertNull(repository.cachedPreview().getOrThrow())
        assertTrue(dao.items("moodle").isEmpty())
        assertNull(dao.syncState("moodle", AgendaDao.SECTION))
        assertNull(courses.readPage("moodle"))
        assertNotNull(dao.readPreview("other"))
        assertEquals(ProviderSession.State.Unknown, repository.session.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid replacements are rejected before deleting a previous snapshot`() {
        dao.replacePreview("moodle", listOf(old), 123)
        val invalid = listOf(listOf(old, old), listOf(old.copy(providerId = "other")),
            listOf(old.copy(startEpoch = 0)), listOf(old.copy(startEpoch = Long.MAX_VALUE)))
        invalid.forEach {
            assertTrue(runCatching { dao.replacePreview("moodle", it, 2) }.isFailure)
            assertEquals(listOf(old), dao.readPreview("moodle")!!.items)
        }
        assertTrue(runCatching { dao.replacePreview("moodle", emptyList(), Long.MAX_VALUE) }.isFailure)
        assertEquals(123L, dao.readPreview("moodle")!!.lastSuccessEpochSeconds)
    }

    @Test
    fun `corrupt epochs and storage failures return safe typed errors`() = runBlocking {
        dao.replacePreview("moodle", listOf(old), 123)
        courses.agendaRows[0] = courses.agendaRows[0].copy(startEpoch = Long.MAX_VALUE)
        assertReason(Reason.STORAGE_ERROR, repository.cachedPreview())
        courses.agendaRows.clear()
        dao.saveSyncState(SyncStateEntity("moodle", AgendaDao.SECTION, Long.MAX_VALUE, null))
        assertReason(Reason.STORAGE_ERROR, repository.cachedPreview())
        courses.failStorage = true
        assertReason(Reason.STORAGE_ERROR, repository.cachedPreview())
        enqueuePage("calendar_ok.json")
        assertReason(Reason.STORAGE_ERROR, repository.fetchPreview())
    }

    @Test
    fun `future cached event kinds are safely represented as other and row order is preserved`() {
        val second = old.copy(remoteId = "2")
        dao.replacePreview("moodle", listOf(old, second), 123)
        courses.agendaRows[0] = courses.agendaRows[0].copy(kind = "FUTURE_KIND")
        val preview = dao.readPreview("moodle")!!
        assertEquals(listOf("99", "2"), preview.items.map { it.remoteId })
        assertEquals(AgendaItem.Kind.OTHER, preview.items.first().kind)
    }

    @Test
    fun `account clearing waits for an in-flight agenda refresh then removes its resulting snapshot`() = runBlocking {
        server.enqueue(MockResponse.Builder().body(fixture("moodle_config_ok.html"))
            .bodyDelay(100, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture("calendar_ok.json")))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val refresh = executor.submit<Boolean> { runBlocking { repository.fetchPreview().isSuccess } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val cleared = executor.submit<Boolean> { runBlocking { CourseRepository(provider, courses, clock).clearCache().isSuccess } }
            assertTrue(refresh.get(5, TimeUnit.SECONDS))
            assertTrue(cleared.get(5, TimeUnit.SECONDS))
            assertNull(repository.cachedPreview().getOrThrow())
            assertTrue(dao.items("moodle").isEmpty())
        } finally { executor.shutdownNow() }
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
    private fun assertReason(reason: Reason, result: Result<*>) {
        val error = result.exceptionOrNull() as PortalException
        assertEquals(reason, error.reason)
        assertNull(error.cause)
        assertFalse(error.stackTraceToString().contains("private-example-input"))
    }
}
