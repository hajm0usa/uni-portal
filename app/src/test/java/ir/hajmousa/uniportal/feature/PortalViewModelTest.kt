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
class PortalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val dao = MemoryCourseDao()
    private val agendaDao = MemoryAgendaDao(dao)
    private val clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC)
    private lateinit var server: MockWebServer
    private lateinit var model: PortalViewModel
    private val old = AgendaItem("moodle", "99", "Synthetic saved deadline", 1700006400, AgendaItem.Kind.DEADLINE)

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().apply { start() }
        createDefaultModel()
    }

    private fun createDefaultModel() {
        val provider = MoodleProvider(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0), clock)
        createModel(provider, provider.courses, provider.agendaPreview)
    }

    private fun createModel(provider: Provider, courses: DataSource<Course>, agenda: DataSource<AgendaItem>) {
        store.clear()
        val sync = SyncCoordinator(provider, CourseRepository(provider, courses, dao, clock), AgendaRepository(provider, agenda, agendaDao, clock), clock)
        val content = CourseContentRepository(provider, { DataSource { Result.success(emptyList()) } },
            { _, _ -> DataSource { Result.success(emptyList()) } }, MemoryCourseContentDao(dao), clock)
        model = PortalViewModel(PortalRepositories(sync, content), clock)
        store.put("portal", model)
    }

    @After
    fun stop() { store.clear(); Dispatchers.resetMain(); server.close() }

    @Test
    fun `startup restores cache without networking and manual refresh loads both sections once`() = runTest(dispatcher) {
        agendaDao.replacePreview("moodle", listOf(old), 123)
        createDefaultModel()
        val restored = model.state.first { !it.loading }
        assertEquals(listOf(old), restored.agenda!!.items)
        assertEquals(0, server.requestCount)
        enqueuePage("courses_ok.json")
        enqueuePage("calendar_ok.json")
        model.refresh(force = true)
        model.refresh(force = true)
        assertTrue(model.state.value.loading)
        val loaded = model.state.first { !it.loading }
        assertEquals(21, loaded.courses!!.courses.size)
        assertEquals("101", loaded.agenda!!.items.single().remoteId)
        assertTrue(loaded.authenticated)
        assertFalse(loaded.needsLogin)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `foreground freshness check leaves recent cache alone and refresh errors retain saved data`() = runTest(dispatcher) {
        dao.replacePage("moodle", emptyList(), 1700000000)
        agendaDao.replacePreview("moodle", listOf(old), 1700000000)
        createDefaultModel()
        model.state.first { !it.loading }
        model.refresh()
        model.state.first { !it.loading }
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse(code = 500))
        server.enqueue(MockResponse(code = 500))
        model.refresh(force = true)
        val failed = model.state.first { !it.loading }
        assertEquals(listOf(old), failed.agenda!!.items)
        assertEquals(Reason.SERVER_ERROR, failed.agendaError)
        assertEquals(Reason.SERVER_ERROR, failed.coursesError)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `expiry retains content needs login and does not automatically retry on later foregrounds`() = runTest(dispatcher) {
        agendaDao.replacePreview("moodle", listOf(old), 123)
        createDefaultModel()
        model.state.first { !it.loading }
        server.enqueue(MockResponse(body = "<html>Synthetic login</html>"))
        model.refresh()
        val expired = model.state.first { !it.loading }
        assertTrue(expired.needsLogin)
        assertEquals(listOf(old), expired.agenda!!.items)
        model.refresh()
        model.state.first { !it.loading }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `account clearing immediately hides both sections and blocks login on storage failure`() = runTest(dispatcher) {
        dao.replacePage("moodle", listOf(Course("moodle", "1", "Sample", null)), 123)
        agendaDao.replacePreview("moodle", listOf(old), 123)
        createDefaultModel()
        model.state.first { !it.loading }
        dao.failStorage = true
        var opened = false
        model.clearForAccountChange { opened = true }
        assertNull(model.state.value.courses)
        assertNull(model.state.value.agenda)
        val failed = model.state.first { !it.clearing }
        assertEquals(Reason.STORAGE_ERROR, failed.agendaError)
        assertFalse(opened)
        dao.failStorage = false
        model.clearForAccountChange { opened = true }
        model.state.first { !it.clearing }
        assertTrue(opened)
        assertNull(agendaDao.readPreview("moodle"))
        assertNull(dao.readPage("moodle"))
    }

    @Test
    fun `leaving foreground cancels refresh before another section starts and never strands loading`() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>()
        var agendaCalls = 0
        val courses = DataSource<Course> { entered.complete(Unit); awaitCancellation() }
        val agenda = DataSource<AgendaItem> { agendaCalls++; Result.success(emptyList()) }
        val provider = object : Provider {
            override val id = "moodle"
            override val session = ProviderSession()
            override val sources = listOf(courses, agenda)
        }
        createModel(provider, courses, agenda)
        model.state.first { !it.loading }
        model.refresh(force = true)
        entered.await()
        model.pauseRefresh()
        model.state.first { !it.loading }
        assertEquals(0, agendaCalls)
        model.clearForAccountChange {}
        model.state.first { !it.clearing }
        assertFalse(model.state.value.loading)
    }

    private fun enqueuePage(name: String) {
        server.enqueue(MockResponse(body = fixture("moodle_config_ok.html")))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = fixture(name)))
    }
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/moodle/$name")).readText()
}
