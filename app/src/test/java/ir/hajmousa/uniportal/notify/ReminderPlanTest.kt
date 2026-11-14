package ir.hajmousa.uniportal.notify

import ir.hajmousa.uniportal.core.PortalException.Reason
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.WidgetAgendaCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReminderPlanTest {
    private val now = 1700000000L
    private val item = AgendaItem("moodle", "1", "Sample deadline", now + 2 * 86400, AgendaItem.Kind.DEADLINE)
    private val cache = WidgetAgendaCache(listOf(item), now)

    @Test
    fun `future cached deadlines and exams get exactly twenty-four and three hour offsets`() {
        val exam = item.copy(remoteId = "2", kind = AgendaItem.Kind.EXAM)
        val plan = reminderPlan(cache.copy(items = listOf(item, exam, item.copy(remoteId = "3", kind = AgendaItem.Kind.OTHER))), now)
        assertEquals(4, plan.size)
        assertEquals(listOf(item.startEpoch - 86400, item.startEpoch - 10800), plan.take(2).map { it.triggerEpoch })
    }

    @Test
    fun `past trigger times and expired events never become new alarms`() {
        val soon = item.copy(startEpoch = now + 3600)
        val plan = reminderPlan(cache.copy(items = listOf(soon, item.copy(remoteId = "2", startEpoch = now))), now)
        assertEquals(2, plan.size)
        assertTrue(reminderChanges(emptyList(), plan, now, false).schedule.isEmpty())
        assertTrue(reminderChanges(emptyList(), reminderPlan(cache, now), item.startEpoch - 10800, true)
            .schedule.isEmpty())
    }

    @Test
    fun `add change and delete cancel obsolete versions without rearming unchanged alarms`() {
        val previous = reminderPlan(cache, now)
        assertEquals(ReminderChanges(emptyList(), emptyList()), reminderChanges(previous, previous, now, false))
        val changed = reminderPlan(cache.copy(items = listOf(item.copy(startEpoch = item.startEpoch + 3600))), now)
        assertEquals(ReminderChanges(previous, changed), reminderChanges(previous, changed, now, false))
        assertEquals(ReminderChanges(previous, emptyList()), reminderChanges(previous, emptyList(), now, false))
        assertEquals(ReminderChanges(emptyList(), previous), reminderChanges(emptyList(), previous, now, false))
    }

    @Test
    fun `boot clock-change or permission grant forces all still-future alarms to be restored`() {
        val plan = reminderPlan(cache, now)
        assertEquals(plan, reminderChanges(plan, plan, now, true).schedule)
        assertEquals(listOf(plan.last()), reminderChanges(plan, plan, item.startEpoch - 86400, true).schedule)
    }

    @Test
    fun `provider identity preserves duplicate titles and remote ids and has no additional preview cap`() {
        val items = (1..8).map { item.copy(remoteId = "$it") } + item.copy(providerId = "other")
        assertEquals(18, reminderPlan(cache.copy(items = items + item), now).size)
    }

    @Test
    fun `delivery rereads matching version and rejects early deleted changed expired or unverified kinds`() {
        val spec = reminderPlan(cache, now).first()
        assertNull(reminderItem(cache, spec, spec.triggerEpoch - 1))
        assertEquals(item, reminderItem(cache, spec, spec.triggerEpoch))
        assertNull(reminderItem(cache, spec, spec.startEpoch))
        assertNull(reminderItem(WidgetAgendaCache(), spec, spec.triggerEpoch))
        assertNull(reminderItem(cache.copy(items = listOf(item.copy(startEpoch = item.startEpoch + 1))), spec, spec.triggerEpoch))
        assertNull(reminderItem(cache.copy(items = listOf(item.copy(kind = AgendaItem.Kind.OTHER))), spec, spec.triggerEpoch))
        assertNull(reminderItem(cache.copy(items = listOf(item.copy(providerId = "other"))), spec, spec.triggerEpoch))
    }

    @Test
    fun `known deadlines remain local reminders during session expiry or failed refresh`() {
        val failed = cache.copy(needsLogin = true, error = Reason.SESSION_EXPIRED)
        assertEquals(reminderPlan(cache, now), reminderPlan(failed, now))
        val spec = reminderPlan(cache, now).first()
        assertEquals(item, reminderItem(failed, spec, spec.triggerEpoch))
    }

    @Test
    fun `cancellation ledger stores no titles links or session data and survives process recreation`() {
        val plan = reminderPlan(cache, now)
        val encoded = Json.encodeToString(plan)
        assertEquals(plan, Json.decodeFromString<List<ReminderSpec>>(encoded))
        listOf(item.title, "deepLink", "sesskey", "cookie", "password").forEach { assertFalse(encoded.contains(it)) }
    }

    @Test
    fun `invalid reminder metadata fails closed before an alarm can be scheduled`() {
        listOf(
            { ReminderSpec("", "1", item.startEpoch, 86400) },
            { ReminderSpec("moodle", "", item.startEpoch, 86400) },
            { ReminderSpec("moodle", "1", 0, 86400) },
            { ReminderSpec("moodle", "1", Long.MAX_VALUE, 86400) },
            { ReminderSpec("moodle", "1", item.startEpoch, 1) },
        ).forEach { invalid ->
            try { invalid(); fail("Invalid reminder accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
