package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException

data class CachedCoursePage(
    val courses: List<Course>,
    val lastSuccessEpochSeconds: Long,
    val lastError: PortalException.Reason? = null,
)
