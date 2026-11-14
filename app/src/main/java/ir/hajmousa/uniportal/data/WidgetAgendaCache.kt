package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException

data class WidgetAgendaCache(
    val items: List<AgendaItem> = emptyList(),
    val lastSuccessEpochSeconds: Long? = null,
    val needsLogin: Boolean = false,
    val error: PortalException.Reason? = null,
)
