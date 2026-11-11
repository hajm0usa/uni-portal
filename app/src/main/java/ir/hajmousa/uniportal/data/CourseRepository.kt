package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CookieJar
import java.time.Clock

class CourseRepository internal constructor(
    private val provider: Provider,
    private val courses: DataSource<Course>,
    private val dao: CourseDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal constructor(provider: MoodleProvider, dao: CourseDao, clock: Clock = Clock.systemUTC()) :
        this(provider, provider.courses, dao, clock)

    constructor(cookieJar: CookieJar, userAgent: String, dao: CourseDao) : this(MoodleProvider(cookieJar, userAgent), dao)

    val session = provider.session.state

    // shortcut: reread on startup/refresh only, switch to DAO Flow when another cache writer is added.
    suspend fun cachedFirstPage(): Result<CachedCoursePage?> = cacheLock.withLock {
        storage {
            val page = dao.readPage(provider.id)
            if (dao.syncState(provider.id, CourseDao.SECTION)?.lastError == PortalException.Reason.SESSION_EXPIRED.name) {
                provider.session.requireLogin()
            }
            page
        }
    }

    suspend fun fetchFirstPage(): Result<CachedCoursePage> = cacheLock.withLock {
        val fetched = courses.fetch()
        val result = if (fetched.isSuccess) {
            storage {
                dao.replacePage(provider.id, fetched.getOrThrow(), clock.instant().epochSecond)
                requireNotNull(dao.readPage(provider.id))
            }
        } else {
            val reason = (fetched.exceptionOrNull() as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
            val recorded = storage { dao.recordFailure(provider.id, reason) }
            Result.failure(recorded.exceptionOrNull() ?: PortalException(reason))
        }
        val event = when {
            result.isSuccess -> AppLog.Event.COURSES_LOADED
            (result.exceptionOrNull() as? PortalException)?.reason == PortalException.Reason.SESSION_EXPIRED ->
                AppLog.Event.SESSION_EXPIRED
            else -> AppLog.Event.COURSE_LOAD_FAILED
        }
        AppLog.record(event, result.exceptionOrNull())
        result
    }

    suspend fun clearCache(): Result<Unit> = cacheLock.withLock {
        storage { dao.clearPage(provider.id) }.also { if (it.isSuccess) provider.session.reset() }
    }

    private fun <T> storage(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (_: Exception) {
        AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
        Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
    }

    private companion object {
        // Serialize clearing with in-flight refreshes, even across recreated repository instances.
        val cacheLock = Mutex()
    }
}
