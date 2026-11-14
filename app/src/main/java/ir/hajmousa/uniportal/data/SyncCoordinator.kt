package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncSnapshot(
    val courses: Result<CachedCoursePage?>,
    val agenda: Result<CachedAgendaPreview?>,
)

class SyncCoordinator internal constructor(
    private val provider: Provider,
    private val courses: CourseRepository,
    private val agenda: AgendaRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    val session = provider.session.state
    val newAgendaItemKeys = agenda.newItemKeys
    private val lock get() = lockFor(provider.id)

    suspend fun cached(): SyncSnapshot = lock.withLock {
        SyncSnapshot(courses.cachedFirstPage(), agenda.cachedPreview())
    }

    suspend fun syncIfStale(force: Boolean = false): SyncSnapshot = lock.withLock {
        val savedCourses = courses.cachedFirstPage()
        val savedAgenda = agenda.cachedPreview()
        if (session.value == ProviderSession.State.NeedsLogin) return@withLock expired()
        val now = clock.instant().epochSecond
        val courseResult = if (force || isStale(savedCourses.getOrNull()?.lastSuccessEpochSeconds, now)) {
            courses.fetchFirstPage()
        } else savedCourses
        // A dead shared session must not start another section's login/retry sequence.
        if (session.value == ProviderSession.State.NeedsLogin) {
            return@withLock SyncSnapshot(courseResult, Result.failure(PortalException(PortalException.Reason.SESSION_EXPIRED)))
        }
        val agendaResult = if (force || isStale(savedAgenda.getOrNull()?.lastSuccessEpochSeconds, now)) {
            agenda.fetchPreview()
        } else savedAgenda
        SyncSnapshot(courseResult, agendaResult)
    }

    suspend fun clearForAccountChange(): Result<Unit> = lock.withLock { courses.clearCache() }

    suspend fun markAgendaItemSeen(item: AgendaItem): Result<Unit> = lock.withLock {
        if (item.providerId != provider.id) Result.failure(PortalException(PortalException.Reason.PARSE_ERROR))
        else agenda.markSeen(item.remoteId)
    }

    private fun expired() = SyncSnapshot(
        Result.failure(PortalException(PortalException.Reason.SESSION_EXPIRED)),
        Result.failure(PortalException(PortalException.Reason.SESSION_EXPIRED)),
    )

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        internal fun lockFor(providerId: String): Mutex = locks.getOrPut(providerId) { Mutex() }
        fun isStale(lastSuccessEpoch: Long?, nowEpoch: Long): Boolean =
            lastSuccessEpoch == null || nowEpoch < lastSuccessEpoch || nowEpoch - lastSuccessEpoch >= 30 * 60
    }
}
