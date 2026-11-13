package ir.hajmousa.uniportal.feature

import ir.hajmousa.uniportal.data.AgendaItem
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TodayAgendaTest {
    private val now = Instant.parse("2026-11-13T10:00:00Z").epochSecond
    private fun event(id: String, offset: Long, kind: AgendaItem.Kind = AgendaItem.Kind.DEADLINE) =
        AgendaItem("moodle", id, "Same title", now + offset, kind)

    @Test
    fun `nearest future deadline is featured once without hiding other event kinds`() {
        val earlierEvent = event("1", 60, AgendaItem.Kind.OTHER)
        val nearest = event("2", 120)
        val later = event("3", 180)
        val result = todayAgenda(listOf(later, nearest, earlierEvent), now)
        assertEquals(nearest, result.featured)
        assertEquals(listOf(earlierEvent, later), result.days.values.flatten())
    }

    @Test
    fun `no UI event count cap or title based deduplication`() {
        val all = (1..20).map { event(it.toString(), it * 60L) }
        val result = todayAgenda(all.reversed(), now)
        assertEquals(all, listOfNotNull(result.featured) + result.days.values.flatten())
    }

    @Test
    fun `past deadlines today remain visible but are not featured`() {
        val past = event("1", -60)
        val next = event("2", 60)
        val result = todayAgenda(listOf(past, next), now)
        assertEquals(next, result.featured)
        assertEquals(listOf(past), result.days.values.flatten())
    }

    @Test
    fun `Tehran dates not UTC decide which days are visible`() {
        val midnight = Instant.parse("2026-11-13T20:30:00Z").epochSecond
        val before = event("1", 0).copy(startEpoch = midnight - 1)
        val after = event("2", 0).copy(startEpoch = midnight)
        val result = todayAgenda(listOf(before, after), midnight)
        assertEquals(after, result.featured)
        assertTrue(result.days.isEmpty())
        assertEquals(listOf(LocalDate.of(2026, 11, 14)),
            todayAgenda(listOf(after.copy(kind = AgendaItem.Kind.OTHER)), midnight).days.keys.toList())
    }

    @Test
    fun `provider identity not just remote ID excludes the featured event`() {
        val first = event("1", 60)
        val other = first.copy(providerId = "other")
        val result = todayAgenda(listOf(first, other), now)
        assertEquals(first, result.featured)
        assertEquals(listOf(other), result.days.values.flatten())
    }

    @Test
    fun `empty feed and feed without deadlines do not invent a featured card`() {
        assertEquals(TodayAgenda(null, emptyMap()), todayAgenda(emptyList(), now))
        val item = event("1", 60, AgendaItem.Kind.OTHER)
        val result = todayAgenda(listOf(item), now)
        assertNull(result.featured)
        assertEquals(listOf(item), result.days.values.flatten())
    }
}
