package ir.hajmousa.uniportal.feature

import ir.hajmousa.uniportal.core.time.tehranDate
import ir.hajmousa.uniportal.data.AgendaItem
import java.time.LocalDate

internal data class TodayAgenda(val featured: AgendaItem?, val days: Map<LocalDate, List<AgendaItem>>)

internal fun todayAgenda(items: List<AgendaItem>, nowEpoch: Long): TodayAgenda {
    val today = tehranDate(nowEpoch)
    val visible = items.filter { tehranDate(it.startEpoch) >= today }.sortedBy { it.startEpoch }
    val featured = visible.firstOrNull { it.kind == AgendaItem.Kind.DEADLINE && it.startEpoch >= nowEpoch }
    val remaining = visible.filterNot { it.providerId == featured?.providerId && it.remoteId == featured.remoteId }
    return TodayAgenda(featured, remaining.groupBy { tehranDate(it.startEpoch) })
}
