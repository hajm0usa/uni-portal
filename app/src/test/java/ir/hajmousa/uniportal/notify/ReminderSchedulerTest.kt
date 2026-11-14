package ir.hajmousa.uniportal.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
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
                    R.string.reminder_cached_note, R.string.app_name -> "Synthetic notification label"
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
        val scheduled = shadowOf(alarms).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertEquals(listOf(item.startEpoch - 86400, item.startEpoch - 10800), scheduled.map { it.triggerAtTime / 1000 })
        assertTrue(scheduled.all { it.type == AlarmManager.RTC_WAKEUP && it.isAllowWhileIdle })
        scheduler().reschedule()
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test
    fun `changed deadlines replace alarms and account clearing cancels every old alarm`() = runBlocking {
        scheduler().reschedule()
        val changed = item.copy(startEpoch = item.startEpoch + 3600)
        db.agendaDao().replacePreview("moodle", listOf(changed), now + 1)
        scheduler().reschedule()
        assertEquals(listOf(changed.startEpoch - 86400, changed.startEpoch - 10800),
            shadowOf(alarms).scheduledAlarms.map { it.triggerAtTime / 1000 })
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
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
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
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
    }

    @Test
    fun `URI identity prevents hash collisions and cross-provider ids from replacing each others alarms`() = runBlocking {
        val first = spec.copy(remoteId = "Aa")
        val second = spec.copy(remoteId = "BB")
        assertEquals(reminderUri(first).toString().hashCode(), reminderUri(second).toString().hashCode())
        db.agendaDao().replacePreview("moodle", listOf(item.copy(remoteId = "Aa"), item.copy(remoteId = "BB")), now)
        db.agendaDao().replacePreview("other", listOf(item.copy(providerId = "other", remoteId = "Aa")), now)
        scheduler().reschedule()
        assertEquals(6, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(6, shadowOf(alarms).scheduledAlarms.map { it.operation }.distinct().size)
    }
}
