package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException.Reason
import org.junit.Assert.*
import org.junit.Test

class WidgetAgendaCacheTest {
    private val courses = MemoryCourseDao()
    private val agenda = MemoryAgendaDao(courses)
    private val item = AgendaItem("moodle", "1", "Sample", 1700000100, AgendaItem.Kind.DEADLINE)

    @Test
    fun `Room snapshot merges provider scoped previews using the oldest successful fetch time`() {
        agenda.replacePreview("moodle", listOf(item), 100)
        agenda.replacePreview("other", listOf(item.copy(providerId = "other")), 200)
        val cache = agenda.readWidgetAgenda()
        assertEquals(listOf(item, item.copy(providerId = "other")), cache.items)
        assertEquals(100L, cache.lastSuccessEpochSeconds)
        assertFalse(cache.needsLogin)
    }

    @Test
    fun `expiry from courses or content is visible even without a successful agenda fetch`() {
        courses.recordFailure("moodle", Reason.SESSION_EXPIRED)
        assertTrue(agenda.readWidgetAgenda().needsLogin)
        courses.clearPage("moodle")
        courses.saveSyncState(SyncStateEntity("moodle", "content:folder:1:2", null, Reason.SESSION_EXPIRED.name))
        assertTrue(agenda.readWidgetAgenda().needsLogin)
        assertNull(agenda.readWidgetAgenda().lastSuccessEpochSeconds)
    }

    @Test
    fun `failed refresh retains successful data and account clear removes widget data and expiry`() {
        agenda.replacePreview("moodle", listOf(item), 100)
        agenda.recordFailure("moodle", Reason.NETWORK_ERROR)
        assertEquals(WidgetAgendaCache(listOf(item), 100, error = Reason.NETWORK_ERROR), agenda.readWidgetAgenda())
        agenda.recordFailure("moodle", Reason.SESSION_EXPIRED)
        assertTrue(agenda.readWidgetAgenda().needsLogin)
        courses.clearPage("moodle")
        assertEquals(WidgetAgendaCache(), agenda.readWidgetAgenda())
    }

    @Test
    fun `failed first fetch is not mistaken for an empty successful preview`() {
        agenda.recordFailure("moodle", Reason.NETWORK_ERROR)
        assertEquals(Reason.NETWORK_ERROR, agenda.readWidgetAgenda().error)
        assertNull(agenda.readWidgetAgenda().lastSuccessEpochSeconds)
        agenda.replacePreview("moodle", emptyList(), 100)
        assertEquals(WidgetAgendaCache(lastSuccessEpochSeconds = 100), agenda.readWidgetAgenda())
    }
}
