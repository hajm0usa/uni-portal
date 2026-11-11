package ir.hajmousa.uniportal.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {
    @Before
    fun setUp() = AppLog.clear()

    @After
    fun tearDown() = AppLog.clear()

    @Test
    fun `oldest entry is evicted at capacity`() {
        AppLog.record(AppLog.Event.LOG_SCREEN_OPENED)
        repeat(AppLog.CAPACITY) { AppLog.record(AppLog.Event.APP_STARTED) }
        assertEquals(AppLog.CAPACITY, AppLog.snapshot().size)
        assertTrue(AppLog.snapshot().all { it.event == AppLog.Event.APP_STARTED })
    }

    @Test
    fun `errors retain stack frames but never messages causes or suppressed text`() {
        val secrets = listOf(
            "https://example.invalid/?sesskey=secret-value",
            "Cookie: MoodleSession=private-cookie",
            "token=single-use-ticket",
            "private message body",
        )
        val error = IllegalStateException(secrets.joinToString(), RuntimeException(secrets[1]))
        error.addSuppressed(RuntimeException(secrets[3]))
        AppLog.record(AppLog.Event.APP_STARTED, error)
        val output = AppLog.snapshot().toString()
        secrets.forEach { assertFalse(output.contains(it)) }
        assertTrue(output.contains("java.lang.IllegalStateException"))
        assertTrue(output.contains("AppLogTest"))
    }

    @Test
    fun `snapshot survives clear and stack frames are bounded`() {
        val error = RuntimeException().apply {
            stackTrace = Array(100) { StackTraceElement("Example", "run", "Example.kt", it) }
        }
        AppLog.record(AppLog.Event.APP_STARTED, error)
        val snapshot = AppLog.snapshot()
        AppLog.clear()
        assertTrue(AppLog.snapshot().isEmpty())
        assertEquals(1, snapshot.size)
        assertEquals(33, snapshot.single().diagnostic!!.lines().size)
    }

    @Test
    fun `concurrent writers keep the buffer bounded`() {
        val writers = List(4) {
            Thread { repeat(500) { AppLog.record(AppLog.Event.APP_STARTED) } }
        }
        writers.forEach { it.start() }
        writers.forEach { it.join() }
        assertEquals(AppLog.CAPACITY, AppLog.snapshot().size)
    }
}
