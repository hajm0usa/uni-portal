package ir.hajmousa.uniportal.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.data.PortalDatabase
import ir.hajmousa.uniportal.data.SyncCoordinator
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DEADLINES_CHANNEL = "deadlines"
private const val SESSION_CHANNEL = "session"
private const val SESSION_NOTIFICATION_TAG = "session"
private const val SESSION_NOTIFIED_EPOCH = "session_notified_epoch"

internal fun reminderNotificationsEnabled(context: Context, channelId: String = DEADLINES_CHANNEL): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java)
    return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled() && manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
}

internal fun allReminderNotificationsEnabled(context: Context): Boolean =
    reminderNotificationsEnabled(context) && reminderNotificationsEnabled(context, SESSION_CHANNEL)

internal class ReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val database: PortalDatabase = PortalDatabase.get(context),
) {
    private val preferences get() = context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    suspend fun reschedule(force: Boolean = false) = withContext(Dispatchers.IO) {
        safely {
            lock.withLock {
                createChannels()
                val now = clock.instant().epochSecond
                val current = if (reminderNotificationsEnabled(context)) reminderPlan(database.agendaDao().readWidgetAgenda(), now) else emptyList()
                val previous = readScheduled()
                val changes = reminderChanges(previous, current, now, force)
                val currentTags = current.associateBy { reminderUri(it).toString() }
                notifications.activeNotifications.filter { it.notification.channelId == DEADLINES_CHANNEL }.forEach { active ->
                    val spec = currentTags[active.tag]
                    if (spec == null || active.notification.`when` != spec.startEpoch * 1000) notifications.cancel(active.tag, active.id)
                }
                changes.cancel.forEach { spec ->
                    alarmIntent(spec, PendingIntent.FLAG_NO_CREATE)?.let { pending -> alarms.cancel(pending); pending.cancel() }
                    notifications.cancel(reminderUri(spec).toString(), 0)
                }
                changes.schedule.forEach { spec ->
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.triggerEpoch * 1000,
                        requireNotNull(alarmIntent(spec, PendingIntent.FLAG_UPDATE_CURRENT)))
                }
                // Keep elapsed offsets until the event expires so changed/deleted items also clear delivered notifications.
                check(preferences.edit().putString("scheduled", json.encodeToString(current)).commit())
                reconcileSession(now)
            }
        }
    }

    suspend fun deliver(spec: ReminderSpec) = withContext(Dispatchers.IO) {
        safely {
            lock.withLock {
                SyncCoordinator.lockFor(spec.providerId).withLock provider@{
                    createChannels()
                    if (!reminderNotificationsEnabled(context)) return@provider
                    val item = reminderItem(database.agendaDao().readWidgetAgenda(), spec, clock.instant().epochSecond) ?: return@provider
                    val open = PendingIntent.getActivity(context, 0, reminderOpenIntent(context, spec),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val publicVersion = Notification.Builder(context, DEADLINES_CHANNEL)
                        .setSmallIcon(R.drawable.home_clock).setContentTitle(context.getString(R.string.app_name))
                        .setContentText(context.getString(R.string.reminder_public)).build()
                    val notification = Notification.Builder(context, DEADLINES_CHANNEL)
                        .setSmallIcon(R.drawable.home_clock).setContentTitle(item.title)
                        .setContentText(formatPersianDateTime(item.startEpoch))
                        .setWhen(item.startEpoch * 1000).setShowWhen(false)
                        .setSubText(context.getString(R.string.reminder_cached_note))
                        .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).build()
                    notifications.notify(reminderUri(spec).toString(), 0, notification)
                }
            }
        }
    }

    suspend fun deliverSession(lastSuccess: Long) = withContext(Dispatchers.IO) {
        safely {
            lock.withLock {
                createChannels()
                postSessionReminder(lastSuccess, clock.instant().epochSecond)
            }
        }
    }

    private fun reconcileSession(now: Long) {
        val lastSuccess = database.agendaDao().lastSuccessfulSync()
        val due = sessionReminderEpoch(lastSuccess)
        val allowed = reminderNotificationsEnabled(context, SESSION_CHANNEL)
        val active = notifications.activeNotifications.firstOrNull { it.tag == SESSION_NOTIFICATION_TAG }
        if (lastSuccess == null || due == null || !allowed || active?.notification?.`when` != lastSuccess * 1000) {
            notifications.cancel(SESSION_NOTIFICATION_TAG, 0)
        }
        sessionIntent(lastSuccess ?: 0, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it) }
        if (due == null || !allowed) {
            if (lastSuccess == null && preferences.contains(SESSION_NOTIFIED_EPOCH)) {
                check(preferences.edit().remove(SESSION_NOTIFIED_EPOCH).commit())
            }
            return
        }
        if (preferences.getLong(SESSION_NOTIFIED_EPOCH, -1) == lastSuccess) return
        if (due > now) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due * 1000,
                requireNotNull(sessionIntent(requireNotNull(lastSuccess), PendingIntent.FLAG_UPDATE_CURRENT)))
        } else {
            // Boot/reopen can discover an overdue alarm; notify once for this successful-sync epoch.
            postSessionReminder(requireNotNull(lastSuccess), now)
        }
    }

    private fun postSessionReminder(expectedSuccess: Long, now: Long) {
        database.runInTransaction {
            val latest = database.agendaDao().lastSuccessfulSync()
            val due = sessionReminderEpoch(latest) ?: return@runInTransaction
            if (latest != expectedSuccess || now < due || !reminderNotificationsEnabled(context, SESSION_CHANNEL) ||
                preferences.getLong(SESSION_NOTIFIED_EPOCH, -1) == latest) return@runInTransaction
            val open = PendingIntent.getActivity(context, 0, sessionOpenIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(context, SESSION_CHANNEL)
                .setSmallIcon(R.drawable.home_clock).setContentTitle(context.getString(R.string.session_reminder_title))
                .setContentText(context.getString(R.string.session_reminder_body))
                .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.session_reminder_body)))
                .setWhen(expectedSuccess * 1000).setShowWhen(false).setContentIntent(open)
                .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
            notifications.notify(SESSION_NOTIFICATION_TAG, 0, notification)
            check(preferences.edit().putLong(SESSION_NOTIFIED_EPOCH, expectedSuccess).commit())
        }
    }

    private fun sessionIntent(lastSuccess: Long, flags: Int): PendingIntent? = PendingIntent.getBroadcast(context,
        0, sessionAlarmIntent(context, lastSuccess), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun createChannels() {
        notifications.createNotificationChannel(NotificationChannel(DEADLINES_CHANNEL,
            context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        notifications.createNotificationChannel(NotificationChannel(SESSION_CHANNEL,
            context.getString(R.string.session_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.session_reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }

    private fun alarmIntent(spec: ReminderSpec, flags: Int): PendingIntent? = PendingIntent.getBroadcast(context,
        reminderUri(spec).toString().hashCode(), reminderIntent(context, spec), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun readScheduled(): List<ReminderSpec> = try {
        json.decodeFromString(preferences.getString("scheduled", "[]") ?: "[]")
    } catch (_: Exception) {
        // shortcut: a corrupt ledger can leave harmless wakeups; move bookkeeping to Room if cancellation must survive corruption.
        AppLog.record(AppLog.Event.REMINDER_FAILED)
        emptyList()
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AppLog.record(AppLog.Event.REMINDER_FAILED) }
    }

    companion object {
        private val lock = Mutex()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
