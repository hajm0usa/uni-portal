package ir.hajmousa.uniportal.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.room.Room
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.PortalDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var db: PortalDatabase
    private val now = 1700000000L
    private val item = AgendaItem("moodle", "1", "Synthetic deadline", now + 172800, AgendaItem.Kind.DEADLINE)
    private val spec get() = ReminderSpec(item.providerId, item.remoteId, item.startEpoch, 86400)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val deadlineAlarms get() = shadowOf(alarms).scheduledAlarms.filter {
        shadowOf(requireNotNull(it.operation)).savedIntent.action == REMINDER_ACTION
    }
    private val sessionAlarms get() = shadowOf(alarms).scheduledAlarms.filter {
        shadowOf(requireNotNull(it.operation)).savedIntent.action == SESSION_REMINDER_ACTION
    }

    private fun scheduler(at: Long = now) = ReminderScheduler(context,
        Clock.fixed(Instant.ofEpochSecond(at), ZoneOffset.UTC), db)

    @Before
    fun start() {
        val app = RuntimeEnvironment.getApplication()
        // Supply only presentation strings so these tests also run without AGP resource linking.
        context = object : ContextWrapper(app) {
            private val strings = object : Resources(app.assets, app.resources.displayMetrics, app.resources.configuration) {
                override fun getText(id: Int): CharSequence = when (id) {
                    R.string.reminder_channel, R.string.reminder_channel_description, R.string.reminder_public,
                    R.string.reminder_cached_note, R.string.app_name, R.string.session_reminder_channel,
                    R.string.session_reminder_channel_description, R.string.session_reminder_title,
                    R.string.session_reminder_body -> "Synthetic notification label"
                    else -> super.getText(id)
                }
            }
            override fun getResources() = strings
        }
        db = Room.inMemoryDatabaseBuilder(context, PortalDatabase::class.java).allowMainThreadQueries().build()
        context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE).edit().clear().commit()
        notifications.cancelAll()
        shadowOf(notifications).setNotificationsEnabled(true)
        AppLog.clear()
        db.agendaDao().replacePreview("moodle", listOf(item), now)
    }

    @After
    fun stop() { db.close(); AppLog.clear() }

    @Test
    fun `platform schedules two idle-allowed RTC alarms and unchanged recomputation does not duplicate them`() = runBlocking {
        scheduler().reschedule()
        val scheduled = deadlineAlarms
        assertEquals(2, scheduled.size)
        assertEquals(listOf(item.startEpoch - 86400, item.startEpoch - 10800), scheduled.map { it.triggerAtTime / 1000 })
        assertTrue(scheduled.all { it.type == AlarmManager.RTC_WAKEUP && it.isAllowWhileIdle })
        scheduler().reschedule()
        assertEquals(2, deadlineAlarms.size)
    }

    @Test
    fun `changed deadlines replace alarms and account clearing cancels every old alarm`() = runBlocking {
        scheduler().reschedule()
        val changed = item.copy(startEpoch = item.startEpoch + 3600)
        db.agendaDao().replacePreview("moodle", listOf(changed), now + 1)
        scheduler().reschedule()
        assertEquals(listOf(changed.startEpoch - 86400, changed.startEpoch - 10800),
            deadlineAlarms.map { it.triggerAtTime / 1000 })
        db.courseDao().clearPage("moodle")
        scheduler().reschedule()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun `delivery uses current Room title and time with a redacted lock-screen version and clears after logout`() = runBlocking {
        scheduler().reschedule()
        scheduler(spec.triggerEpoch).deliver(spec)
        val notification = notifications.activeNotifications.single().notification
        assertEquals(item.title, notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(formatPersianDateTime(item.startEpoch), notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertFalse(notification.publicVersion.extras.getString(Notification.EXTRA_TITLE)!!.contains(item.title))
        assertNotNull(notification.contentIntent)
        db.courseDao().clearPage("moodle")
        scheduler(spec.triggerEpoch).reschedule()
        scheduler(spec.triggerEpoch).deliver(spec)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(AppLog.snapshot().none { it.diagnostic?.contains(item.title) == true })
    }

    @Test
    fun `queued stale deadline cannot deliver after a changed snapshot`() = runBlocking {
        db.agendaDao().replacePreview("moodle", listOf(item.copy(startEpoch = item.startEpoch + 1)), now + 1)
        scheduler(spec.triggerEpoch).deliver(spec)
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test
    fun `boot-style forced restore recreates future alarms from Room after system alarms are lost`() = runBlocking {
        scheduler().reschedule()
        shadowOf(alarms).scheduledAlarms.toList().forEach { alarms.cancel(requireNotNull(it.operation)) }
        scheduler().reschedule(force = true)
        assertEquals(2, deadlineAlarms.size)
    }

    @Test
    fun `notification denial suppresses delivered content even with a corrupt cancellation ledger`() = runBlocking {
        scheduler().reschedule()
        scheduler(spec.triggerEpoch).deliver(spec)
        context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE).edit().putString("scheduled", "invalid").commit()
        shadowOf(notifications).setNotificationsEnabled(false)
        scheduler(spec.triggerEpoch).reschedule()
        assertTrue(notifications.activeNotifications.isEmpty())
        scheduler(spec.triggerEpoch).deliver(spec)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(AppLog.snapshot().none { it.diagnostic?.contains("invalid") == true })
    }

    @Test
    fun `notification denial cancels scheduled alarms and enabling notifications restores them`() = runBlocking {
        scheduler().reschedule()
        shadowOf(notifications).setNotificationsEnabled(false)
        scheduler().reschedule()
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        shadowOf(notifications).setNotificationsEnabled(true)
        scheduler().reschedule()
        assertEquals(2, deadlineAlarms.size)
    }

    @Test
    fun `URI identity prevents hash collisions and cross-provider ids from replacing each others alarms`() = runBlocking {
        val first = spec.copy(remoteId = "Aa")
        val second = spec.copy(remoteId = "BB")
        assertEquals(reminderUri(first).toString().hashCode(), reminderUri(second).toString().hashCode())
        db.agendaDao().replacePreview("moodle", listOf(item.copy(remoteId = "Aa"), item.copy(remoteId = "BB")), now)
        db.agendaDao().replacePreview("other", listOf(item.copy(providerId = "other", remoteId = "Aa")), now)
        scheduler().reschedule()
        assertEquals(6, deadlineAlarms.size)
        assertEquals(6, deadlineAlarms.map { it.operation }.distinct().size)
    }

    @Test
    fun `successful sync postpones the single three-day alarm but failed refresh does not`() = runBlocking {
        scheduler().reschedule()
        assertEquals((now + SESSION_REMINDER_DELAY) * 1000, sessionAlarms.single().triggerAtTime)
        db.courseDao().replacePage("moodle", emptyList(), now + 3600)
        scheduler(now + 3600).reschedule()
        assertEquals((now + 3600 + SESSION_REMINDER_DELAY) * 1000, sessionAlarms.single().triggerAtTime)
        db.agendaDao().recordFailure("moodle", ir.hajmousa.uniportal.core.PortalException.Reason.NETWORK_ERROR)
        scheduler(now + 7200).reschedule()
        assertEquals((now + 3600 + SESSION_REMINDER_DELAY) * 1000, sessionAlarms.single().triggerAtTime)
    }

    @Test
    fun `session reminder cannot fire early and notifies once per successful sync with no account or item data`() = runBlocking {
        val due = now + SESSION_REMINDER_DELAY
        scheduler(due - 1).deliverSession(now)
        assertTrue(notifications.activeNotifications.isEmpty())
        scheduler(due).deliverSession(now)
        val notification = notifications.activeNotifications.single().notification
        assertEquals("session", notification.channelId)
        assertEquals("Synthetic notification label", notification.extras.getString(Notification.EXTRA_TEXT))
        assertFalse(notification.extras.toString().contains(item.title))
        assertFalse(notification.extras.toString().contains(item.providerId))
        assertTrue(isSessionReminderIntent(shadowOf(requireNotNull(notification.contentIntent)).savedIntent))
        notifications.cancelAll()
        scheduler(due + 1).deliverSession(now)
        scheduler(due + 1).reschedule(force = true)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(sessionAlarms.isEmpty())
    }

    @Test
    fun `newer sync and logout invalidate queued session alarms`() = runBlocking {
        val due = now + SESSION_REMINDER_DELAY
        db.courseDao().replacePage("moodle", emptyList(), now + 1)
        scheduler(due).deliverSession(now)
        assertTrue(notifications.activeNotifications.isEmpty())
        scheduler(due + 1).deliverSession(now + 1)
        assertEquals(1, notifications.activeNotifications.size)
        db.courseDao().clearPage("moodle")
        scheduler(due + 2).reschedule()
        scheduler(due + 2).deliverSession(now + 1)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(sessionAlarms.isEmpty())
    }

    @Test
    fun `a later successful sync removes the old session notification and arms the new three-day interval`() = runBlocking {
        val due = now + SESSION_REMINDER_DELAY
        scheduler(due).deliverSession(now)
        db.courseDao().replacePage("moodle", emptyList(), due + 1)
        scheduler(due + 1).reschedule()
        assertTrue(notifications.activeNotifications.isEmpty())
        assertEquals((due + 1 + SESSION_REMINDER_DELAY) * 1000, sessionAlarms.single().triggerAtTime)
    }

    @Test
    fun `boot-style restore handles an overdue session once without a repeating past alarm`() = runBlocking {
        val late = now + SESSION_REMINDER_DELAY + 3600
        scheduler(late).reschedule(force = true)
        assertEquals(1, notifications.activeNotifications.size)
        assertTrue(sessionAlarms.isEmpty())
        notifications.cancelAll()
        scheduler(late).reschedule(force = true)
        assertTrue(notifications.activeNotifications.isEmpty())
        assertTrue(sessionAlarms.isEmpty())
    }

    @Test
    fun `blocked session channel does not suppress deadline alarms`() = runBlocking {
        notifications.createNotificationChannel(NotificationChannel("session", "Synthetic blocked channel", NotificationManager.IMPORTANCE_NONE))
        scheduler().reschedule()
        assertEquals(2, deadlineAlarms.size)
        assertTrue(sessionAlarms.isEmpty())
        assertTrue(reminderNotificationsEnabled(context))
        assertFalse(allReminderNotificationsEnabled(context))
        scheduler(now + SESSION_REMINDER_DELAY).deliverSession(now)
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test
    fun `permission grant can deliver an overdue session notice but denial cannot mark it delivered`() = runBlocking {
        val due = now + SESSION_REMINDER_DELAY
        shadowOf(notifications).setNotificationsEnabled(false)
        scheduler(due).reschedule()
        scheduler(due).deliverSession(now)
        assertTrue(notifications.activeNotifications.isEmpty())
        shadowOf(notifications).setNotificationsEnabled(true)
        scheduler(due).reschedule(force = true)
        assertEquals(1, notifications.activeNotifications.size)
    }
}
