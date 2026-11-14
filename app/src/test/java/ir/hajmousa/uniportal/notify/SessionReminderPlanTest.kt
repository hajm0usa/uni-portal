package ir.hajmousa.uniportal.notify

import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.MemoryAgendaDao
import ir.hajmousa.uniportal.data.MemoryCourseDao
import org.junit.Assert.*
import org.junit.Test

class SessionReminderPlanTest {
    @Test
    fun `no successful sync means no session alarm`() {
        assertNull(sessionReminderEpoch(null))
    }

    @Test
    fun `three-day interval is elapsed epoch time not a guessed local timezone offset`() {
        assertEquals(1700259200L, sessionReminderEpoch(1700000000))
        assertEquals(259200L, sessionReminderEpoch(0))
    }

    @Test
    fun `invalid or overflowing persisted epochs cannot become alarm timestamps`() {
        assertNull(sessionReminderEpoch(-1))
        assertNull(sessionReminderEpoch(Long.MAX_VALUE))
        val lastValid = Long.MAX_VALUE / 1000 - SESSION_REMINDER_DELAY
        assertEquals(Long.MAX_VALUE / 1000, sessionReminderEpoch(lastValid))
        assertNull(sessionReminderEpoch(lastValid + 1))
    }

    @Test
    fun `failures preserve the last success while any successful section read pushes the baseline forward`() {
        val courses = MemoryCourseDao()
        val agenda = MemoryAgendaDao(courses)
        agenda.replacePreview("moodle", emptyList(), 100)
        val first = sessionReminderEpoch(agenda.lastSuccessfulSync())
        agenda.recordFailure("moodle", Reason.SESSION_EXPIRED)
        assertEquals(first, sessionReminderEpoch(agenda.lastSuccessfulSync()))
        courses.replacePage("moodle", emptyList(), 200)
        assertEquals(first!! + 100, sessionReminderEpoch(agenda.lastSuccessfulSync()))
        courses.clearPage("moodle")
        assertNull(sessionReminderEpoch(agenda.lastSuccessfulSync()))
    }
}
