package ir.hajmousa.uniportal.widget

import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.WidgetAgendaCache
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class AgendaWidgetStateTest {
    private val clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC)
    private val item = AgendaItem("moodle", "1", "Sample", 1700000100, AgendaItem.Kind.DEADLINE)

    @Test
    fun `only future deadlines and exams appear ordered by epoch not title or input order`() {
        val exam = item.copy(remoteId = "2", startEpoch = 1700000200, kind = AgendaItem.Kind.EXAM)
        val cache = WidgetAgendaCache(listOf(exam, item.copy(remoteId = "3", kind = AgendaItem.Kind.OTHER),
            item.copy(remoteId = "4", startEpoch = 1700000000), item.copy(remoteId = "5", startEpoch = 1699999999), item), 1700000000)
        assertEquals(listOf(item, exam), widgetState(cache, clock).upcoming)
        assertFalse(widgetState(cache, clock).stale)
    }

    @Test
    fun `same title and cross provider ids stay distinct and no extra feed count cap is imposed`() {
        val items = (1..8).map { item.copy(remoteId = "$it") } + item.copy(providerId = "other")
        val state = widgetState(WidgetAgendaCache(items + item, 1700000000), clock)
        assertEquals(9, state.upcoming.size)
        assertEquals(items.toSet(), state.upcoming.toSet())
    }

    @Test
    fun `missing old future-dated and failed caches are stale without discarding successful data`() {
        assertTrue(widgetState(WidgetAgendaCache(), clock).stale)
        listOf(1699998200L, 1700000001L).forEach {
            assertTrue(widgetState(WidgetAgendaCache(listOf(item), it), clock).stale)
        }
        val failed = WidgetAgendaCache(listOf(item), 1700000000, error = Reason.NETWORK_ERROR)
        assertTrue(widgetState(failed, clock).stale)
        assertEquals(listOf(item), widgetState(failed, clock).upcoming)
    }

    @Test
    fun `empty success differs from never loaded and expiry preserves the cached preview`() {
        val empty = widgetState(WidgetAgendaCache(lastSuccessEpochSeconds = 1700000000), clock)
        assertFalse(empty.stale)
        assertTrue(empty.upcoming.isEmpty())
        val expired = widgetState(WidgetAgendaCache(listOf(item), 1700000000, needsLogin = true), clock)
        assertTrue(expired.cache.needsLogin)
        assertEquals(listOf(item), expired.upcoming)
    }
}
