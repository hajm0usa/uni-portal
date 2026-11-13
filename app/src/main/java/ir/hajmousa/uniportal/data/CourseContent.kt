package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import kotlinx.serialization.Serializable

@Serializable
data class CourseSection(
    val providerId: String,
    val remoteId: String,
    val courseRemoteId: String,
    val title: String,
    val activities: List<CourseActivity>,
)

@Serializable
data class CourseActivity(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val kind: Kind,
    val deepLink: String?,
) {
    enum class Kind { FOLDER, ASSIGNMENT, LABEL, OTHER }
}

@Serializable
data class CourseFile(
    val providerId: String,
    val remoteId: String,
    val courseRemoteId: String,
    val activityRemoteId: String,
    val name: String,
    val url: String,
)

data class CachedContent<T>(
    val items: List<T>,
    val lastSuccessEpochSeconds: Long,
    val lastError: PortalException.Reason? = null,
)
