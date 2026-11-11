package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleAjaxClient
import okhttp3.CookieJar
import java.time.Clock

class CourseRepository internal constructor(
    private val client: MoodleAjaxClient,
    private val dao: CourseDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    constructor(cookieJar: CookieJar, userAgent: String, dao: CourseDao) : this(MoodleAjaxClient(cookieJar, userAgent), dao)

    // shortcut: reread on startup/refresh only, switch to DAO Flow when another cache writer is added.
    fun cachedFirstPage(): Result<CachedCoursePage?> = synchronized(cacheLock) {
        storage { dao.readPage(PROVIDER_ID) }
    }

    fun fetchFirstPage(): Result<CachedCoursePage> = synchronized(cacheLock) {
        val fetched = client.fetchFirstCoursePage()
        val result = if (fetched.isSuccess) {
            storage {
                dao.replacePage(PROVIDER_ID, fetched.getOrThrow().courses, clock.instant().epochSecond)
                requireNotNull(dao.readPage(PROVIDER_ID))
            }
        } else {
            val reason = (fetched.exceptionOrNull() as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
            val recorded = storage { dao.recordFailure(PROVIDER_ID, reason) }
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

    fun clearCache(): Result<Unit> = synchronized(cacheLock) {
        storage { dao.clearPage(PROVIDER_ID) }
    }

    private fun <T> storage(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (_: Exception) {
        AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
        Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
    }

    private companion object {
        const val PROVIDER_ID = "moodle"
        // Serialize clearing with in-flight refreshes, even across recreated repository instances.
        val cacheLock = Any()
    }
}
