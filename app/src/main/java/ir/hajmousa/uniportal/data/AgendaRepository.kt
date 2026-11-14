package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.provider.moodle.MoodleProvider
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

class AgendaRepository internal constructor(
    private val provider: Provider,
    private val source: DataSource<AgendaItem>,
    private val dao: AgendaDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal constructor(provider: MoodleProvider, dao: AgendaDao, clock: Clock = Clock.systemUTC()) :
        this(provider, provider.agendaPreview, dao, clock)

    val session = provider.session.state

    val newItemKeys: Flow<Result<Set<Pair<String, String>>>> = dao.observeNewItems(provider.id)
        .map { items -> Result.success(items.map { it.providerId to it.remoteId }.toSet()) }
        .catch {
            AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
            emit(Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR)))
        }.flowOn(Dispatchers.IO)

    suspend fun markSeen(remoteId: String): Result<Unit> = CourseRepository.cacheLock.withLock {
        storage { dao.markItemSeen(provider.id, remoteId) }
    }

    // shortcut: reread on startup/refresh like courses, switch to DAO Flow when another writer is added.
    suspend fun cachedPreview(): Result<CachedAgendaPreview?> = CourseRepository.cacheLock.withLock {
        storage {
            val preview = dao.readPreview(provider.id)
            if (dao.syncState(provider.id, AgendaDao.SECTION)?.lastError == PortalException.Reason.SESSION_EXPIRED.name) {
                provider.session.requireLogin()
            }
            preview
        }
    }

    suspend fun fetchPreview(): Result<CachedAgendaPreview> = CourseRepository.cacheLock.withLock {
        val fetched = source.fetch()
        val result = if (fetched.isSuccess) {
            storage {
                dao.replacePreview(provider.id, fetched.getOrThrow(), clock.instant().epochSecond)
                requireNotNull(dao.readPreview(provider.id))
            }
        } else {
            val reason = (fetched.exceptionOrNull() as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
            val recorded = storage { dao.recordFailure(provider.id, reason) }
            Result.failure(recorded.exceptionOrNull() ?: PortalException(reason))
        }
        AppLog.record(when {
            result.isSuccess -> AppLog.Event.AGENDA_LOADED
            provider.session.state.value == ProviderSession.State.NeedsLogin -> AppLog.Event.SESSION_EXPIRED
            else -> AppLog.Event.AGENDA_LOAD_FAILED
        }, result.exceptionOrNull())
        result
    }

    private fun <T> storage(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (_: Exception) {
        AppLog.record(AppLog.Event.CACHE_STORAGE_FAILED)
        Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
    }
}
