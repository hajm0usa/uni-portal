package ir.hajmousa.uniportal

import android.app.Application
import androidx.room.InvalidationTracker
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.data.PortalDatabase
import ir.hajmousa.uniportal.notify.ReminderScheduler
import ir.hajmousa.uniportal.widget.updateAgendaWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PortalApplication : Application() {
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        cacheScope.launch {
            try {
                PortalDatabase.get(this@PortalApplication).invalidationTracker.addObserver(object : InvalidationTracker.Observer("agenda_item", "sync_state") {
                    override fun onInvalidated(tables: Set<String>) {
                        cacheScope.launch {
                            updateAgendaWidgets(this@PortalApplication)
                            ReminderScheduler(this@PortalApplication).reschedule()
                        }
                    }
                })
            } catch (_: Exception) {
                AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
            }
            updateAgendaWidgets(this@PortalApplication)
            ReminderScheduler(this@PortalApplication).reschedule(force = true)
        }
    }
}
