package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.CachedCoursePage
import ir.hajmousa.uniportal.data.CourseRepository
import ir.hajmousa.uniportal.data.ProviderSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoursesState(
    val page: CachedCoursePage? = null,
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val needsLogin: Boolean = false,
    val error: PortalException.Reason? = null,
    val clearing: Boolean = false,
)

class CoursesViewModel(private val repository: CourseRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CoursesState(loading = true))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { repository.cachedFirstPage() }
            val page = cached.getOrNull()
            val reason = cached.exceptionOrNull()?.let { PortalException.Reason.STORAGE_ERROR } ?: page?.lastError
            mutableState.value = CoursesState(
                page = page, fromCache = page != null,
                needsLogin = repository.session.value == ProviderSession.State.NeedsLogin,
                error = reason?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED },
            )
        }
    }

    fun load() {
        if (mutableState.value.loading || mutableState.value.clearing) return
        mutableState.value = mutableState.value.copy(
            loading = true, fromCache = mutableState.value.page != null,
            needsLogin = false, error = null,
        )
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.fetchFirstPage() }
            mutableState.value = result.fold(
                onSuccess = { CoursesState(page = it) },
                onFailure = {
                    val reason = (it as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
                    mutableState.value.copy(
                        loading = false, fromCache = mutableState.value.page != null,
                        needsLogin = repository.session.value == ProviderSession.State.NeedsLogin,
                        error = reason.takeUnless { it == PortalException.Reason.SESSION_EXPIRED },
                    )
                },
            )
        }
    }

    fun clearForAccountChange(onCleared: () -> Unit) {
        if (mutableState.value.clearing) return
        loadJob?.cancel()
        mutableState.value = CoursesState(clearing = true)
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.clearCache() }
            mutableState.value = if (result.isSuccess) CoursesState() else CoursesState(error = PortalException.Reason.STORAGE_ERROR)
            if (result.isSuccess) onCleared()
        }
    }
}
