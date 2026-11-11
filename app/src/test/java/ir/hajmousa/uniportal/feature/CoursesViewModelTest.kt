package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModelStore
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CourseRepository
import ir.hajmousa.uniportal.data.MemoryCourseDao
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CoursesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var model: CoursesViewModel
    private val store = ViewModelStore()
    private val dao = MemoryCourseDao()

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().apply { start() }
        createModel()
        AppLog.clear()
    }

    private fun createModel() {
        store.clear()
        model = CoursesViewModel(CourseRepository(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0), dao))
        store.put("courses", model)
    }

    @After
    fun stop() {
        store.clear()
        Dispatchers.resetMain()
        server.close()
        AppLog.clear()
    }

    @Test
    fun `first page loads without duplicate requests and account clearing resets it`() = runTest(dispatcher) {
        model.state.first { !it.loading }
        assertEquals(CoursesState(), model.state.value)
        enqueueCourses()
        model.load()
        model.load()
        assertTrue(model.state.value.loading)
        val loaded = model.state.first { it.page != null && !it.loading }
        assertEquals(21, loaded.page!!.courses.size)
        assertFalse(loaded.fromCache)
        assertEquals(2, server.requestCount)
        assertEquals(AppLog.Event.COURSES_LOADED, AppLog.snapshot().last().event)
        var cleared = false
        model.clearForAccountChange { cleared = true }
        model.state.first { !it.clearing }
        assertTrue(cleared)
        assertEquals(CoursesState(), model.state.value)
        assertNull(dao.readPage("moodle"))
    }

    @Test
    fun `expired session preserves saved content and successful re-login refresh replaces it`() = runTest(dispatcher) {
        val old = seedCache()
        model.state.first { !it.loading }
        server.enqueue(MockResponse(body = "<html>synthetic login page</html>"))
        model.load()
        val expired = model.state.first { it.needsLogin }
        assertEquals(listOf(old), expired.page!!.courses)
        assertTrue(expired.fromCache)
        assertEquals(AppLog.Event.SESSION_EXPIRED, AppLog.snapshot().last().event)
        enqueueCourses()
        model.load()
        val loaded = model.state.first { !it.loading && !it.needsLogin }
        assertEquals(21, loaded.page!!.courses.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `server error keeps cached content and diagnostics never contain response text`() = runTest(dispatcher) {
        val old = seedCache()
        model.state.first { !it.loading }
        server.enqueue(MockResponse(code = 500, body = "private-example-input"))
        model.load()
        val failed = model.state.first { it.error != null }
        assertEquals(Reason.SERVER_ERROR, failed.error)
        assertEquals(listOf(old), failed.page!!.courses)
        assertTrue(failed.fromCache)
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
    }

    @Test
    fun `startup restores cached rows and persistent session error without any network request`() = runTest(dispatcher) {
        val old = seedCache()
        dao.recordFailure("moodle", Reason.SESSION_EXPIRED)
        val restored = model.state.first { !it.loading }
        assertEquals(listOf(old), restored.page!!.courses)
        assertTrue(restored.fromCache)
        assertTrue(restored.needsLogin)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `failed cache clearing hides old content and blocks account-change callback`() = runTest(dispatcher) {
        seedCache()
        model.state.first { !it.loading }
        dao.failStorage = true
        var openedAnotherAccount = false
        model.clearForAccountChange { openedAnotherAccount = true }
        assertNull(model.state.value.page)
        val failed = model.state.first { !it.clearing }
        assertEquals(Reason.STORAGE_ERROR, failed.error)
        assertFalse(openedAnotherAccount)
        assertNull(failed.page)
    }

    private fun seedCache(): Course {
        val course = Course("moodle", "99", "synthetic saved course", null)
        dao.replacePage("moodle", listOf(course), 1700000000)
        createModel()
        return course
    }

    private fun enqueueCourses() {
        server.enqueue(MockResponse(body = requireNotNull(javaClass.getResource("/fixtures/moodle/moodle_config_ok.html")).readText()))
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json"), body = requireNotNull(javaClass.getResource("/fixtures/moodle/courses_ok.json")).readText()))
    }
}
