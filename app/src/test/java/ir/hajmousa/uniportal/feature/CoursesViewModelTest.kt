package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModelStore
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.CourseRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var model: CoursesViewModel
    private val store = ViewModelStore()

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer().apply { start() }
        model = CoursesViewModel(CourseRepository(MoodleAjaxClient(CookieJar.NO_COOKIES, "UniPortal/test", server.url("/"), 0)))
        store.put("courses", model)
        AppLog.clear()
    }

    @After
    fun stop() {
        store.clear()
        Dispatchers.resetMain()
        server.close()
        AppLog.clear()
    }

    @Test
    fun `first page transitions idle loading content and reset without duplicate requests`() = runTest(dispatcher) {
        enqueueCourses()
        assertEquals(CoursesState.Idle, model.state.value)
        model.load()
        model.load()
        assertEquals(CoursesState.Loading, model.state.value)
        val loaded = model.state.first { it is CoursesState.Loaded } as CoursesState.Loaded
        assertEquals(21, loaded.courses.size)
        assertEquals(2, server.requestCount)
        assertEquals(AppLog.Event.COURSES_LOADED, AppLog.snapshot().last().event)
        model.reset()
        assertEquals(CoursesState.Idle, model.state.value)
    }

    @Test
    fun `expired session shows needs login and a successful retry replaces it`() = runTest(dispatcher) {
        server.enqueue(MockResponse(body = "<html>synthetic login page</html>"))
        model.load()
        model.state.first { it == CoursesState.NeedsLogin }
        assertEquals(AppLog.Event.SESSION_EXPIRED, AppLog.snapshot().last().event)
        enqueueCourses()
        model.load()
        val loaded = model.state.first { it is CoursesState.Loaded } as CoursesState.Loaded
        assertEquals(21, loaded.courses.size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `server error is a section error and diagnostics never contain the response`() = runTest(dispatcher) {
        server.enqueue(MockResponse(code = 500, body = "private-example-input"))
        model.load()
        val failed = model.state.first { it is CoursesState.Failed } as CoursesState.Failed
        assertEquals(Reason.SERVER_ERROR, failed.reason)
        val logs = AppLog.snapshot()
        assertEquals(AppLog.Event.COURSE_LOAD_FAILED, logs.last().event)
        assertFalse(logs.toString().contains("private-example-input"))
        assertTrue(logs.none { it.diagnostic?.contains("http://") == true })
    }

    private fun enqueueCourses() {
        server.enqueue(MockResponse(body = requireNotNull(javaClass.getResource("/fixtures/moodle/moodle_config_ok.html")).readText()))
        server.enqueue(MockResponse(
            headers = headersOf("Content-Type", "application/json"),
            body = requireNotNull(javaClass.getResource("/fixtures/moodle/courses_ok.json")).readText(),
        ))
    }
}
