package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModelStore
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.*
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CourseContentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var server: MockWebServer
    private lateinit var provider: MoodleProvider
    private lateinit var repository: CourseContentRepository
    private lateinit var model: CourseContentViewModel
    private lateinit var contentDao: MemoryCourseContentDao
    private val course = Course("moodle", "1", "Sample", null)
    private val clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC)

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().apply { start() }
        provider = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0))
        contentDao = MemoryCourseContentDao()
        repository = CourseContentRepository(provider, contentDao, clock)
        model = CourseContentViewModel(repository, clock)
        store.put("content", model)
    }
    @After
    fun stop() { store.clear(); Dispatchers.resetMain(); server.close() }

    @Test
    fun `nested course and folder navigation reads only selected pages and back keeps the parent snapshot`() = runTest(dispatcher) {
        model.openCourse(course)
        assertEquals(0, server.requestCount)
        enqueuePage("course_content_ok.html")
        model.resume()
        model.refresh()
        val loaded = model.state.first { !it.loading }
        assertEquals(4, loaded.sections!!.size)
        assertEquals(1700000000L, loaded.sectionsUpdatedEpoch)
        assertEquals(2, server.requestCount)
        model.resume()
        assertEquals(2, server.requestCount)
        val folder = loaded.sections.first().activities.last { it.kind == CourseActivity.Kind.FOLDER }
        model.openFolder(folder)
        assertEquals(2, server.requestCount)
        enqueuePage("folder_ok.html")
        model.resume()
        val files = model.state.first { !it.loading }
        assertEquals(2, files.files!!.size)
        assertEquals(1700000000L, files.filesUpdatedEpoch)
        model.back()
        assertNull(model.state.value.folder)
        assertNull(model.state.value.files)
        assertEquals(loaded.sections, model.state.value.sections)
        model.resume()
        assertEquals(4, server.requestCount)
        model.back()
        assertEquals(CourseContentState(), model.state.value)
    }

    @Test
    fun `refresh error retains prior content and fetch time without exposing server text`() = runTest(dispatcher) {
        model.openCourse(course)
        enqueuePage("course_content_ok.html")
        model.refresh()
        val old = model.state.first { !it.loading }
        server.enqueue(MockResponse(code = 500, body = "private-example-input"))
        model.refresh()
        val failed = model.state.first { !it.loading }
        assertEquals(old.sections, failed.sections)
        assertEquals(old.sectionsUpdatedEpoch, failed.sectionsUpdatedEpoch)
        assertEquals(Reason.SERVER_ERROR, failed.error)
        assertFalse(failed.toString().contains("private-example-input"))
    }

    @Test
    fun `content expiry updates the dashboard session and blocks further content networking until login`() = runTest(dispatcher) {
        val courses = contentDao.courses
        val sync = SyncCoordinator(provider, CourseRepository(provider, courses, clock),
            AgendaRepository(provider, MemoryAgendaDao(courses), clock), clock)
        val dashboard = PortalViewModel(PortalRepositories(sync, repository), clock)
        store.put("dashboard", dashboard)
        dashboard.state.first { !it.loading }
        model.openCourse(course)
        server.enqueue(MockResponse(body = fixture("content_login_synthetic.html")))
        model.refresh()
        val expired = model.state.first { !it.loading }
        assertTrue(expired.needsLogin)
        assertTrue(dashboard.state.first { it.needsLogin }.needsLogin)
        model.refresh()
        model.state.first { !it.loading }
        assertEquals(1, server.requestCount)
        model.close()
        dashboard.clearForAccountChange {}
        dashboard.state.first { !it.clearing }
        assertEquals(CourseContentState(), model.state.value)
        assertFalse(dashboard.state.value.needsLogin)
    }

    @Test
    fun `forged folders and foreign provider selections cannot start content requests`() = runTest(dispatcher) {
        model.openCourse(course.copy(providerId = "other"))
        assertNull(model.state.value.course)
        model.openCourse(course)
        val fake = CourseActivity("moodle", "205", "Fake", CourseActivity.Kind.FOLDER,
            "https://elearn.ut.ac.ir/mod/folder/view.php?id=205")
        model.openFolder(fake)
        assertNull(model.state.value.folder)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancelled navigation cannot clear the next loading state or restore a previous accounts content`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val fakeProvider = object : Provider {
            override val id = "moodle"
            override val session = ProviderSession()
            override val sources = emptyList<DataSource<*>>()
        }
        val repository = CourseContentRepository(fakeProvider, { id -> DataSource {
            if (id == "1") { entered.complete(Unit); awaitCancellation() }
            Result.success(listOf(CourseSection("moodle", "200", id, "Second", emptyList())))
        } }, { _, _ -> DataSource { Result.success(emptyList()) } }, MemoryCourseContentDao(), clock)
        val selected = CourseContentViewModel(repository, clock)
        store.put("selected", selected)
        selected.openCourse(course)
        selected.resume()
        entered.await()
        selected.openCourse(course.copy(remoteId = "2"))
        selected.resume()
        val second = selected.state.first { !it.loading }
        assertEquals("2", second.course!!.remoteId)
        assertEquals("2", second.sections!!.single().courseRemoteId)
        selected.stopAndClear()
        assertEquals(CourseContentState(), selected.state.value)
    }

    @Test
    fun `browser boundary permits only plain read-only Moodle views`() {
        assertEquals("https://elearn.ut.ac.ir/course/view.php?id=1", CourseContentRepository.courseBrowserUrl("1"))
        assertNull(CourseContentRepository.courseBrowserUrl("1&sesskey=XXXX"))
        val safe = "https://elearn.ut.ac.ir/mod/forum/view.php?id=201"
        assertEquals(safe, CourseContentRepository.safeBrowserUrl(safe))
        listOf("$safe&action=delete", "$safe&sesskey=XXXX", "$safe#fragment", safe.replace("https:", "http:"),
            safe.replace("elearn.ut.ac.ir", "evil.invalid"), safe.replace("elearn.ut.ac.ir", "user@elearn.ut.ac.ir"),
            safe.replace("elearn.ut.ac.ir", "elearn.ut.ac.ir:444"), safe.replace("view.php", "delete.php"),
            "https://elearn.ut.ac.ir/login/logout.php?id=1", "javascript:alert(1)",
        ).forEach { assertNull(CourseContentRepository.safeBrowserUrl(it)) }
    }

    @Test
    fun `recreated model restores fresh course and folder lists without any network request`() = runTest(dispatcher) {
        model.openCourse(course)
        enqueuePage("course_content_ok.html")
        model.resume()
        val courseState = model.state.first { !it.loading }
        val folder = courseState.sections!!.first().activities.last { it.kind == CourseActivity.Kind.FOLDER }
        model.openFolder(folder)
        enqueuePage("folder_ok.html")
        model.resume()
        val folderState = model.state.first { !it.loading }
        val count = server.requestCount
        model.close()
        val restored = CourseContentViewModel(CourseContentRepository(provider, contentDao, clock), clock)
        store.put("restored", restored)
        restored.openCourse(course)
        restored.resume()
        val savedCourse = restored.state.first { !it.loading }
        assertEquals(courseState.sections, savedCourse.sections)
        assertEquals(courseState.sectionsUpdatedEpoch, savedCourse.sectionsUpdatedEpoch)
        restored.openFolder(folder)
        restored.resume()
        val savedFolder = restored.state.first { !it.loading }
        assertEquals(folderState.files, savedFolder.files)
        assertEquals(folderState.filesUpdatedEpoch, savedFolder.filesUpdatedEpoch)
        assertEquals(count, server.requestCount)
    }

    @Test
    fun `stale saved page is shown before refresh completes and survives an offline failure`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fakeProvider = object : Provider {
            override val id = "moodle"
            override val session = ProviderSession()
            override val sources = emptyList<DataSource<*>>()
        }
        val sections = listOf(CourseSection("moodle", "100", "1", "Saved", emptyList()))
        contentDao.replaceSections("moodle", "1", sections, 1)
        val repo = CourseContentRepository(fakeProvider, { DataSource {
            entered.complete(Unit)
            release.await()
            Result.failure(ir.hajmousa.uniportal.core.PortalException(Reason.NETWORK_ERROR))
        } }, { _, _ -> DataSource { Result.success(emptyList()) } }, contentDao, clock)
        val restored = CourseContentViewModel(repo, clock)
        store.put("stale", restored)
        restored.openCourse(course)
        restored.resume()
        entered.await()
        assertTrue(restored.state.value.loading)
        assertEquals(sections, restored.state.value.sections)
        assertEquals(1L, restored.state.value.sectionsUpdatedEpoch)
        release.complete(Unit)
        val failed = restored.state.first { !it.loading }
        assertEquals(sections, failed.sections)
        assertEquals(1L, failed.sectionsUpdatedEpoch)
        assertEquals(Reason.NETWORK_ERROR, failed.error)
    }

    @Test
    fun `expired session still allows browsing saved folder lists without making requests`() = runTest(dispatcher) {
        val folder = CourseActivity("moodle", "200", "Folder", CourseActivity.Kind.FOLDER,
            "https://elearn.ut.ac.ir/mod/folder/view.php?id=200")
        contentDao.replaceSections("moodle", "1", listOf(CourseSection("moodle", "100", "1", "Saved", listOf(folder))), 1)
        contentDao.replaceFiles("moodle", "1", "200", emptyList(), 2)
        contentDao.recordFailure("moodle", CourseContentDao.sectionsKey("1"), Reason.SESSION_EXPIRED)
        model.openCourse(course)
        model.resume()
        assertTrue(model.state.first { !it.loading }.needsLogin)
        model.openFolder(folder)
        model.resume()
        val saved = model.state.first { !it.loading }
        assertTrue(saved.needsLogin)
        assertEquals(emptyList<CourseFile>(), saved.files)
        assertEquals(2L, saved.filesUpdatedEpoch)
        assertEquals(0, server.requestCount)
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "text/html"), body = fixture(name)))
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
        .replace("https://example.invalid", "https://elearn.ut.ac.ir")
}
