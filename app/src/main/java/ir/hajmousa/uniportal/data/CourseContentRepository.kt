package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import java.time.Clock
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CourseContentRepository internal constructor(
    private val provider: Provider,
    private val sectionsSource: (String) -> DataSource<CourseSection>,
    private val filesSource: (String, String) -> DataSource<CourseFile>,
    private val dao: CourseContentDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal constructor(provider: MoodleProvider, dao: CourseContentDao, clock: Clock = Clock.systemUTC()) :
        this(provider, provider::courseContent, provider::folderFiles, dao, clock)
    val providerId get() = provider.id
    val session = provider.session.state

    suspend fun cachedSections(courseId: String): Result<CachedContent<CourseSection>?> = SyncCoordinator.lockFor(provider.id).withLock {
        cached(CourseContentDao.sectionsKey(courseId)) { dao.readSections(provider.id, courseId) }
    }

    suspend fun cachedFiles(courseId: String, activityId: String): Result<CachedContent<CourseFile>?> = SyncCoordinator.lockFor(provider.id).withLock {
        cached(CourseContentDao.filesKey(courseId, activityId)) { dao.readFiles(provider.id, courseId, activityId) }
    }

    suspend fun sections(courseId: String): Result<CachedContent<CourseSection>> = SyncCoordinator.lockFor(provider.id).withLock {
        if (courseBrowserUrl(courseId) == null) return@withLock Result.failure(PortalException(PortalException.Reason.PARSE_ERROR))
        fetch(CourseContentDao.sectionsKey(courseId), { sectionsSource(courseId).fetch() }) { items, epoch ->
            dao.replaceSections(provider.id, courseId, items, epoch)
        }
    }

    suspend fun files(courseId: String, activityId: String): Result<CachedContent<CourseFile>> = SyncCoordinator.lockFor(provider.id).withLock {
        if (courseBrowserUrl(courseId) == null || courseBrowserUrl(activityId) == null) {
            return@withLock Result.failure(PortalException(PortalException.Reason.PARSE_ERROR))
        }
        fetch(CourseContentDao.filesKey(courseId, activityId), { filesSource(courseId, activityId).fetch() }) { items, epoch ->
            dao.replaceFiles(provider.id, courseId, activityId, items, epoch)
        }
    }

    private fun <T> cached(key: String, read: () -> T): Result<T> = storage {
        if (dao.syncState(provider.id, key)?.lastError == PortalException.Reason.SESSION_EXPIRED.name) provider.session.requireLogin()
        read()
    }

    private suspend fun <T> fetch(key: String, source: suspend () -> Result<List<T>>, save: (List<T>, Long) -> Unit): Result<CachedContent<T>> {
        if (session.value == ProviderSession.State.NeedsLogin) return expired()
        val fetched = source()
        if (fetched.isSuccess) return storage {
            val epoch = clock.instant().epochSecond
            val items = fetched.getOrThrow()
            save(items, epoch)
            CachedContent(items, epoch)
        }
        val reason = (fetched.exceptionOrNull() as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
        if (reason == PortalException.Reason.SESSION_EXPIRED) provider.session.requireLogin()
        val recorded = storage { dao.recordFailure(provider.id, key, reason) }
        return Result.failure(recorded.exceptionOrNull() ?: PortalException(reason))
    }

    private fun <T> storage(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (_: Exception) {
        AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
        Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
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
