package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CourseRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: CourseRepository
    private val dao = MemoryCourseDao()
    private val old = Course("moodle", "99", "old synthetic course", null)

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        repository = CourseRepository(
            MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0)), dao,
            Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC),
        )
        AppLog.clear()
    }

    @After
    fun stop() {
        server.close()
        AppLog.clear()
    }

    @Test
    fun `successful refresh replaces only this provider snapshot and persists server order and epoch seconds`() = runBlocking {
        dao.replacePage("moodle", listOf(old), 1)
        val sibling = Course("other", "1", "synthetic other course", null)
        dao.replacePage("other", listOf(sibling), 2)
        enqueuePage("courses_ok.json")
        val page = repository.fetchFirstPage().getOrThrow()
        assertEquals(21, page.courses.size)
        assertEquals(listOf("1", "2", "3"), page.courses.take(3).map { it.remoteId })
        assertEquals(1700000000L, page.lastSuccessEpochSeconds)
        assertNull(page.lastError)
        assertEquals(page, repository.cachedFirstPage().getOrThrow())
        assertEquals(listOf(sibling), dao.readPage("other")!!.courses)
        assertEquals(ProviderSession.State.Active, repository.session.value)
    }

    @Test
    fun `failed refresh preserves old courses and timestamp and records only a safe reason`() = runBlocking {
        dao.replacePage("moodle", listOf(old), 123)
        server.enqueue(MockResponse(code = 500, body = "private-example-input"))
        val failure = repository.fetchFirstPage().exceptionOrNull() as PortalException
        assertEquals(Reason.SERVER_ERROR, failure.reason)
        val page = repository.cachedFirstPage().getOrThrow()!!
        assertEquals(listOf(old), page.courses)
        assertEquals(123L, page.lastSuccessEpochSeconds)
        assertEquals(Reason.SERVER_ERROR, page.lastError)
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
    }

    @Test
    fun `successful empty page is cached rather than confused with absent cache`() = runBlocking {
        assertNull(repository.cachedFirstPage().getOrThrow())
        dao.replacePage("moodle", listOf(old), 1)
        enqueuePage("courses_empty_synthetic.json")
        val page = repository.fetchFirstPage().getOrThrow()
        assertTrue(page.courses.isEmpty())
        assertEquals(1700000000L, page.lastSuccessEpochSeconds)
        assertEquals(page, repository.cachedFirstPage().getOrThrow())
    }

    @Test
    fun `cache clearing removes courses and metadata without touching a sibling provider`() = runBlocking {
        dao.replacePage("moodle", listOf(old), 1)
        dao.replacePage("other", emptyList(), 2)
        dao.recordFailure("moodle", Reason.SESSION_EXPIRED)
        repository.cachedFirstPage().getOrThrow()
        assertEquals(ProviderSession.State.NeedsLogin, repository.session.value)
        repository.clearCache().getOrThrow()
        assertEquals(ProviderSession.State.Unknown, repository.session.value)
        assertNull(repository.cachedFirstPage().getOrThrow())
        assertTrue(dao.courses("moodle").isEmpty())
        assertNull(dao.syncState("moodle", CourseDao.SECTION))
        assertNotNull(dao.readPage("other"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cache failures are safe typed errors and never claim clearing succeeded`() = runBlocking {
        dao.failStorage = true
        listOf(repository.cachedFirstPage(), repository.clearCache()).forEach {
            val failure = it.exceptionOrNull() as PortalException
            assertEquals(Reason.STORAGE_ERROR, failure.reason)
            assertNull(failure.cause)
            assertFalse(failure.stackTraceToString().contains("private-example-input"))
        }
    }

    @Test
    fun `corrupt cached epoch is rejected before date formatting can overflow`() = runBlocking {
        dao.saveSyncState(SyncStateEntity("moodle", CourseDao.SECTION, Long.MAX_VALUE, null))
        val failure = repository.cachedFirstPage().exceptionOrNull() as PortalException
        assertEquals(Reason.STORAGE_ERROR, failure.reason)
    }

    @Test
    fun `startup restores login requirement even when no course fetch ever succeeded`() = runBlocking {
        dao.recordFailure("moodle", Reason.SESSION_EXPIRED)
        assertNull(repository.cachedFirstPage().getOrThrow())
        assertEquals(ProviderSession.State.NeedsLogin, repository.session.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid replacement timestamp is rejected before deleting saved courses`() {
        dao.replacePage("moodle", listOf(old), 123)
        try {
            dao.replacePage("moodle", emptyList(), Long.MAX_VALUE)
            fail("Expected invalid timestamp rejection")
        } catch (_: IllegalArgumentException) {
            // Reject the replacement before modifying the previous snapshot.
        }
        val page = dao.readPage("moodle")!!
        assertEquals(listOf(old), page.courses)
        assertEquals(123L, page.lastSuccessEpochSeconds)
    }

    @Test
    fun `account clearing waits for an in-flight refresh and removes its resulting snapshot`() = runBlocking {
        server.enqueue(MockResponse.Builder()
            .body(requireNotNull(javaClass.getResource("/fixtures/moodle/moodle_config_ok.html")).readText())
            .bodyDelay(100, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"),
            body = requireNotNull(javaClass.getResource("/fixtures/moodle/courses_ok.json")).readText()))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val refresh = executor.submit<Boolean> { runBlocking { repository.fetchFirstPage().isSuccess } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val cleared = executor.submit<Boolean> { runBlocking { repository.clearCache().isSuccess } }
            assertTrue(refresh.get(5, TimeUnit.SECONDS))
            assertTrue(cleared.get(5, TimeUnit.SECONDS))
            assertNull(repository.cachedFirstPage().getOrThrow())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `repository uses the supplied provider source and cache namespace`() = runBlocking {
        val session = ProviderSession()
        val course = old.copy(providerId = "synthetic-other")
        val source = DataSource { Result.success(listOf(course)) }
        val provider = object : Provider {
            override val id = "synthetic-other"
            override val session = session
            override val sources = listOf(source)
        }
        dao.replacePage("moodle", listOf(old), 1)
        val other = CourseRepository(provider, source, dao)
        assertEquals(listOf(course), other.fetchFirstPage().getOrThrow().courses)
        assertEquals(listOf(course), other.cachedFirstPage().getOrThrow()!!.courses)
        other.clearCache().getOrThrow()
        assertNull(dao.readPage(provider.id))
        assertEquals(listOf(old), dao.readPage("moodle")!!.courses)
        assertEquals(0, server.requestCount)
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = requireNotNull(javaClass.getResource("/fixtures/moodle/moodle_config_ok.html")).readText()))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()))
    }
}
