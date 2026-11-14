package ir.hajmousa.uniportal.widget

import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.data.WidgetAgendaCache
import java.time.Clock

internal data class AgendaWidgetState(
    val cache: WidgetAgendaCache,
    val upcoming: List<AgendaItem>,
    val stale: Boolean,
)

internal fun widgetState(cache: WidgetAgendaCache, clock: Clock = Clock.systemUTC()): AgendaWidgetState {
    val now = clock.instant().epochSecond
    return AgendaWidgetState(cache,
        cache.items.filter { it.startEpoch > now && (it.kind == AgendaItem.Kind.DEADLINE || it.kind == AgendaItem.Kind.EXAM) }
            .distinctBy { it.providerId to it.remoteId }
            .sortedWith(compareBy({ it.startEpoch }, { it.providerId }, { it.remoteId })),
        cache.error != null || SyncCoordinator.isStale(cache.lastSuccessEpochSeconds, now))
}
