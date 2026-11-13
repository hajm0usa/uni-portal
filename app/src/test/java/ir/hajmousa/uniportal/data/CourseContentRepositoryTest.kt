package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CourseContentRepositoryTest {
    private val provider = object : Provider {
        override val id = "moodle"
        override val session = ProviderSession()
        override val sources = emptyList<DataSource<*>>()
    }
    private val dao = MemoryCourseContentDao()
    private val clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC)
    private val section = CourseSection("moodle", "100", "1", "Sample", listOf(
        CourseActivity("moodle", "200", "Same title", CourseActivity.Kind.FOLDER, "https://elearn.ut.ac.ir/mod/folder/view.php?id=200"),
        CourseActivity("moodle", "201", "Same title", CourseActivity.Kind.LABEL, null)))
    private val file = CourseFile("moodle", "300/mod_folder/content/0/sub/sample.pdf", "1", "200", "sample.pdf",
        "https://elearn.ut.ac.ir/pluginfile.php/300/mod_folder/content/0/sub/sample.pdf?forcedownload=1")
    private var result: Result<List<CourseSection>> = Result.success(listOf(section))
    private var calls = 0
    private fun repository() = CourseContentRepository(provider, { DataSource { calls++; result } },
        { _, _ -> DataSource { calls++; Result.success(listOf(file)) } }, dao, clock)

    @Test
    fun `parsed page snapshots round trip nested activities order file paths and original fetch time`() = runTest {
        val repo = repository()
        assertNull(repo.cachedSections("1").getOrThrow())
        repo.sections("1").getOrThrow()
        repo.files("1", "200").getOrThrow()
        val recreated = repository()
        assertEquals(CachedContent(listOf(section), 1700000000), recreated.cachedSections("1").getOrThrow())
        assertEquals(CachedContent(listOf(file), 1700000000), recreated.cachedFiles("1", "200").getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun `empty success is distinct from missing and provider course folder scopes never collide`() = runTest {
        dao.replaceSections("moodle", "1", emptyList(), 1)
        dao.replaceFiles("moodle", "1", "200", listOf(file), 2)
        dao.replaceFiles("moodle", "2", "200", listOf(file.copy(courseRemoteId = "2")), 3)
        dao.replaceFiles("other", "1", "200", listOf(file.copy(providerId = "other")), 4)
        assertTrue(repository().cachedSections("1").getOrThrow()!!.items.isEmpty())
        assertNull(repository().cachedFiles("1", "201").getOrThrow())
        assertEquals("1", dao.readFiles("moodle", "1", "200")!!.items.single().courseRemoteId)
        assertEquals("2", dao.readFiles("moodle", "2", "200")!!.items.single().courseRemoteId)
        assertEquals("other", dao.readFiles("other", "1", "200")!!.items.single().providerId)
    }

    @Test
    fun `failed refresh keeps the saved page and time and a successful empty refresh clears the error`() = runTest {
        val repo = repository()
        dao.replaceSections("moodle", "1", listOf(section), 1)
        result = Result.failure(PortalException(Reason.NETWORK_ERROR))
        assertEquals(Reason.NETWORK_ERROR, (repo.sections("1").exceptionOrNull() as PortalException).reason)
        assertEquals(CachedContent(listOf(section), 1, Reason.NETWORK_ERROR), repo.cachedSections("1").getOrThrow())
        result = Result.success(emptyList())
        repo.sections("1").getOrThrow()
        assertEquals(CachedContent<CourseSection>(emptyList(), 1700000000), repo.cachedSections("1").getOrThrow())
    }

    @Test
    fun `persisted expiry without a successful page blocks networking after recreation until account reset`() = runTest {
        dao.recordFailure("moodle", CourseContentDao.sectionsKey("1"), Reason.SESSION_EXPIRED)
        val repo = repository()
        assertNull(repo.cachedSections("1").getOrThrow())
        assertEquals(ProviderSession.State.NeedsLogin, repo.session.value)
        assertEquals(Reason.SESSION_EXPIRED, (repo.sections("1").exceptionOrNull() as PortalException).reason)
        assertEquals(0, calls)
        dao.courses.clearPage("moodle")
        provider.session.reset()
        repo.sections("1").getOrThrow()
        assertEquals(1, calls)
    }

    @Test
    fun `storage or corrupt cached payload is a safe storage error not a crash or raw exception`() = runTest {
        val repo = repository()
        dao.courses.failStorage = true
        val failure = repo.cachedSections("1").exceptionOrNull() as PortalException
        assertEquals(Reason.STORAGE_ERROR, failure.reason)
        dao.courses.failStorage = false
        dao.replaceSections("moodle", "1", listOf(section), 1)
        val key = CourseContentDao.sectionsKey("1")
        dao.savePage(CourseContentEntity("moodle", key, "private-example-input"))
        assertEquals(Reason.STORAGE_ERROR, (repo.cachedSections("1").exceptionOrNull() as PortalException).reason)
        repo.sections("1").getOrThrow()
        assertEquals(listOf(section), repo.cachedSections("1").getOrThrow()!!.items)
    }

    @Test
    fun `account clearing waits for content fetch then removes pages and metadata only for that provider`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = CourseContentRepository(provider, { DataSource {
            entered.complete(Unit)
            release.await()
            Result.success(listOf(section))
        } }, { _, _ -> DataSource { Result.success(emptyList()) } }, dao, clock)
        val sync = SyncCoordinator(provider, CourseRepository(provider, DataSource { Result.success(emptyList()) }, dao.courses, clock),
            AgendaRepository(provider, DataSource { Result.success(emptyList()) }, MemoryAgendaDao(dao.courses), clock), clock)
        dao.replaceSections("other", "1", emptyList(), 1)
        dao.replaceFiles("moodle", "1", "200", listOf(file), 1)
        dao.recordFailure("moodle", CourseContentDao.sectionsKey("9"), Reason.SESSION_EXPIRED)
        val fetch = async { repo.sections("1") }
        entered.await()
        val clear = async { sync.clearForAccountChange() }
        assertFalse(clear.isCompleted)
        release.complete(Unit)
        fetch.await().getOrThrow()
        clear.await().getOrThrow()
        assertNull(repo.cachedSections("1").getOrThrow())
        assertNull(repo.cachedFiles("1", "200").getOrThrow())
        assertNull(dao.syncState("moodle", CourseContentDao.sectionsKey("9")))
        assertNotNull(dao.readSections("other", "1"))
    }

    @Test
    fun `invalid keys never reach network and mixed scopes cannot overwrite the saved snapshot`() = runTest {
        val repo = repository()
        assertTrue(repo.sections("1&sesskey=XXXX").isFailure)
        assertTrue(repo.files("1", "../200").isFailure)
        assertEquals(0, calls)
        dao.replaceSections("moodle", "1", listOf(section), 1)
        result = Result.success(listOf(section.copy(providerId = "other")))
        assertTrue(repo.sections("1").isFailure)
        assertEquals(listOf(section), repo.cachedSections("1").getOrThrow()!!.items)
    }
}
