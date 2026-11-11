package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException

data class CachedAgendaPreview(
    val items: List<AgendaItem>,
    val lastSuccessEpochSeconds: Long,
    val lastError: PortalException.Reason? = null,
)
