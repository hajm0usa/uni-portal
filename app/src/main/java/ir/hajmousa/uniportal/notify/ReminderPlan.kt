package ir.hajmousa.uniportal.notify

import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.WidgetAgendaCache
import kotlinx.serialization.Serializable

internal val reminderOffsets = listOf(24 * 60 * 60L, 3 * 60 * 60L)

@Serializable
internal data class ReminderSpec(val providerId: String, val remoteId: String, val startEpoch: Long, val offsetSeconds: Long) {
    init {
        require(providerId.isNotBlank() && providerId.length <= 1024 && remoteId.isNotBlank() && remoteId.length <= 1024)
        require(startEpoch in 1..Long.MAX_VALUE / 1000 && offsetSeconds in reminderOffsets)
    }
    val triggerEpoch get() = startEpoch - offsetSeconds
}

internal fun reminderPlan(cache: WidgetAgendaCache, now: Long): List<ReminderSpec> = cache.items
    .filter { it.startEpoch > now && (it.kind == AgendaItem.Kind.DEADLINE || it.kind == AgendaItem.Kind.EXAM) }
    .distinctBy { it.providerId to it.remoteId }
    .flatMap { item -> reminderOffsets.map { ReminderSpec(item.providerId, item.remoteId, item.startEpoch, it) } }

internal data class ReminderChanges(val cancel: List<ReminderSpec>, val schedule: List<ReminderSpec>)

internal fun reminderChanges(previous: List<ReminderSpec>, current: List<ReminderSpec>, now: Long, force: Boolean): ReminderChanges =
    ReminderChanges(previous.filter { it !in current }, current.filter { it.triggerEpoch > now && (force || it !in previous) })

internal fun reminderItem(cache: WidgetAgendaCache, spec: ReminderSpec, now: Long): AgendaItem? {
    if (now < spec.triggerEpoch || now >= spec.startEpoch) return null
    return cache.items.firstOrNull { it.providerId == spec.providerId && it.remoteId == spec.remoteId &&
        it.startEpoch == spec.startEpoch && (it.kind == AgendaItem.Kind.DEADLINE || it.kind == AgendaItem.Kind.EXAM) }
}
