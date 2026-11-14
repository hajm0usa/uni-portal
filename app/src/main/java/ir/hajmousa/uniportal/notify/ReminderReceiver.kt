package ir.hajmousa.uniportal.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val restore = intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        val spec = reminderSpec(intent)
        val sessionEpoch = sessionAlarmEpoch(intent)
        if (!restore && spec == null && sessionEpoch == null) return
        val result = goAsync()
        scope.launch {
            try {
                val scheduler = ReminderScheduler(context.applicationContext)
                when {
                    restore -> scheduler.reschedule(force = true)
                    sessionEpoch != null -> scheduler.deliverSession(sessionEpoch)
                    else -> scheduler.deliver(requireNotNull(spec))
                }
            } finally { result.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
