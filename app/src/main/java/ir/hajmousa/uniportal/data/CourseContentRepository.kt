package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CourseContentRepository internal constructor(
    private val provider: Provider,
    private val sectionsSource: (String) -> DataSource<CourseSection>,
    private val filesSource: (String, String) -> DataSource<CourseFile>,
) {
    internal constructor(provider: MoodleProvider) : this(provider, provider::courseContent, provider::folderFiles)
    val providerId get() = provider.id
    val session = provider.session.state

    // Content is held by its ViewModel only until Room content caching is implemented.
    suspend fun sections(courseId: String): Result<List<CourseSection>> = SyncCoordinator.lockFor(provider.id).withLock {
        if (session.value == ProviderSession.State.NeedsLogin) expired() else sectionsSource(courseId).fetch()
    }

    suspend fun files(courseId: String, activityId: String): Result<List<CourseFile>> = SyncCoordinator.lockFor(provider.id).withLock {
        if (session.value == ProviderSession.State.NeedsLogin) expired() else filesSource(courseId, activityId).fetch()
    }

    private fun <T> expired(): Result<T> = Result.failure(PortalException(PortalException.Reason.SESSION_EXPIRED))

    companion object {
        fun courseBrowserUrl(courseId: String): String? = courseId.toLongOrNull()?.takeIf { it > 0 && it.toString() == courseId }
            ?.let { "${SsoHosts.MOODLE_URL}course/view.php?id=$it" }

        fun safeBrowserUrl(value: String): String? {
            val url = value.toHttpUrlOrNull() ?: return null
            return url.takeIf {
                it.scheme == "https" && it.host == SsoHosts.MOODLE_HOST && it.port == 443 &&
                    it.username.isEmpty() && it.password.isEmpty() && it.fragment == null &&
                    (it.encodedPath == "/course/view.php" || it.encodedPath.matches(Regex("/mod/[a-z][a-z0-9_]*/view\\.php"))) &&
                    it.querySize == 1 && it.queryParameterNames == setOf("id") && (it.queryParameter("id")?.toLongOrNull() ?: 0) > 0
            }?.toString()
        }
    }
}
