package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CoursesState {
    data object Idle : CoursesState
    data object Loading : CoursesState
    data class Loaded(val courses: List<Course>) : CoursesState
    data object NeedsLogin : CoursesState
    data class Failed(val reason: PortalException.Reason) : CoursesState
}

class CoursesViewModel(private val repository: CourseRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<CoursesState>(CoursesState.Idle)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    fun load() {
        if (mutableState.value == CoursesState.Loading) return
        mutableState.value = CoursesState.Loading
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.fetchFirstPage() }
            mutableState.value = result.fold(
                onSuccess = { CoursesState.Loaded(it) },
                onFailure = {
                    val reason = (it as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
                    if (reason == PortalException.Reason.SESSION_EXPIRED) CoursesState.NeedsLogin else CoursesState.Failed(reason)
                },
            )
        }
    }

    fun reset() {
        loadJob?.cancel()
        mutableState.value = CoursesState.Idle
    }
}
