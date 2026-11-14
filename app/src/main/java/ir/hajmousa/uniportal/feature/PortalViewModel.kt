package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.CachedAgendaPreview
import ir.hajmousa.uniportal.data.AgendaItem
import ir.hajmousa.uniportal.data.CachedCoursePage
import ir.hajmousa.uniportal.data.ProviderSession
import ir.hajmousa.uniportal.data.PortalRepositories
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.data.SyncSnapshot
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PortalState(
    val courses: CachedCoursePage? = null,
    val agenda: CachedAgendaPreview? = null,
    val coursesError: PortalException.Reason? = null,
    val agendaError: PortalException.Reason? = null,
    val loading: Boolean = true,
    val clearing: Boolean = false,
    val needsLogin: Boolean = false,
    val authenticated: Boolean = false,
    val nowEpoch: Long = 0,
    val newAgendaItemKeys: Set<Pair<String, String>> = emptySet(),
    val newItemsError: Boolean = false,
)

// Today and Calendar share the same preview and refresh/account-transition lifecycle.
class PortalViewModel(val repositories: PortalRepositories, private val clock: Clock = Clock.systemUTC()) : ViewModel() {
    private val sync = repositories.sync
    private val mutableState = MutableStateFlow(PortalState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var restored = false
    private var badgeObserver: Job? = null
    private val markingJobs = mutableListOf<Job>()

    init {
        observeBadges()
        viewModelScope.launch {
            sync.session.collect { session ->
                mutableState.value = state.value.copy(needsLogin = session == ProviderSession.State.NeedsLogin,
                    authenticated = session == ProviderSession.State.Active)
            }
        }
        work = viewModelScope.launch {
            apply(withContext(Dispatchers.IO) { sync.cached() })
            restored = true
        }
    }

    fun refresh(force: Boolean = false) {
        if (state.value.loading || state.value.clearing) return
        if (state.value.newItemsError) observeBadges()
        mutableState.value = state.value.copy(loading = true, nowEpoch = clock.instant().epochSecond)
        work = viewModelScope.launch {
            try {
                apply(withContext(Dispatchers.IO) { sync.syncIfStale(force) })
            } finally {
                mutableState.value = state.value.copy(loading = false)
            }
        }
    }

    fun pauseRefresh() {
        if (restored && !state.value.clearing) work?.cancel()
    }

    suspend fun stopRefresh() { work?.cancelAndJoin() }

    fun markAgendaItemSeen(item: AgendaItem) {
        if (state.value.clearing || (item.providerId to item.remoteId) !in state.value.newAgendaItemKeys ||
            state.value.agenda?.items?.none { it.providerId == item.providerId && it.remoteId == item.remoteId } != false) return
        markingJobs.removeAll { it.isCompleted }
        markingJobs += viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sync.markAgendaItemSeen(item) }
            if (result.isFailure) mutableState.value = state.value.copy(newItemsError = true)
            else if (state.value.newItemsError) observeBadges()
        }
    }

    private fun observeBadges() {
        badgeObserver?.cancel()
        badgeObserver = viewModelScope.launch {
            sync.newAgendaItemKeys.collect { result ->
                mutableState.value = state.value.copy(newAgendaItemKeys = result.getOrElse { state.value.newAgendaItemKeys },
                    newItemsError = result.isFailure)
            }
        }
    }

    fun clearForAccountChange(onCleared: () -> Unit) {
        if (state.value.clearing) return
        val previous = work
        badgeObserver?.cancel()
        mutableState.value = PortalState(loading = false, clearing = true)
        work = viewModelScope.launch {
            previous?.cancelAndJoin()
            markingJobs.forEach { it.cancelAndJoin() }
            markingJobs.clear()
            val result = withContext(Dispatchers.IO) { sync.clearForAccountChange() }
            restored = true
            mutableState.value = PortalState(loading = false,
                coursesError = if (result.isFailure) PortalException.Reason.STORAGE_ERROR else null,
                agendaError = if (result.isFailure) PortalException.Reason.STORAGE_ERROR else null)
            observeBadges()
            if (result.isSuccess) onCleared()
        }
    }

    private fun apply(snapshot: SyncSnapshot) {
        val coursePage = snapshot.courses.getOrNull()
        val agendaPage = snapshot.agenda.getOrNull()
        mutableState.value = state.value.copy(
            courses = coursePage ?: state.value.courses,
            agenda = agendaPage ?: state.value.agenda,
            coursesError = reason(snapshot.courses) ?: coursePage?.lastError,
            agendaError = reason(snapshot.agenda) ?: agendaPage?.lastError,
            loading = false,
            needsLogin = sync.session.value == ProviderSession.State.NeedsLogin,
            authenticated = sync.session.value == ProviderSession.State.Active,
            nowEpoch = clock.instant().epochSecond,
        )
    }

    private fun reason(result: Result<*>): PortalException.Reason? = result.exceptionOrNull()?.let {
        (it as? PortalException)?.reason ?: PortalException.Reason.STORAGE_ERROR
    }
}
