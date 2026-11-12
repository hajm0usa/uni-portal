package ir.hajmousa.uniportal.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CourseActivity
import ir.hajmousa.uniportal.data.CourseContentRepository
import ir.hajmousa.uniportal.data.CourseFile
import ir.hajmousa.uniportal.data.CourseSection
import ir.hajmousa.uniportal.data.ProviderSession
import ir.hajmousa.uniportal.data.SyncCoordinator
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class CourseContentState(
    val course: Course? = null,
    val folder: CourseActivity? = null,
    val sections: List<CourseSection>? = null,
    val files: List<CourseFile>? = null,
    val sectionsUpdatedEpoch: Long? = null,
    val filesUpdatedEpoch: Long? = null,
    val nowEpoch: Long = 0,
    val loading: Boolean = false,
    val needsLogin: Boolean = false,
    val error: PortalException.Reason? = null,
)

class CourseContentViewModel(private val repository: CourseContentRepository, private val clock: Clock = Clock.systemUTC()) : ViewModel() {
    private val mutableState = MutableStateFlow(CourseContentState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    fun openCourse(course: Course) {
        if (course.providerId != repository.providerId) return
        work?.cancel()
        mutableState.value = CourseContentState(course = course)
    }

    fun openFolder(folder: CourseActivity) {
        if (folder.kind != CourseActivity.Kind.FOLDER || folder.providerId != repository.providerId ||
            folder.deepLink?.let(CourseContentRepository::safeBrowserUrl) == null ||
            state.value.sections?.flatMap { it.activities }?.contains(folder) != true) return
        work?.cancel()
        mutableState.value = state.value.copy(folder = folder, files = null, filesUpdatedEpoch = null, loading = false, error = null)
    }

    fun back() {
        work?.cancel()
        mutableState.value = if (state.value.folder != null) state.value.copy(folder = null, files = null,
            filesUpdatedEpoch = null, loading = false, error = null) else CourseContentState()
    }

    fun close() { work?.cancel(); mutableState.value = CourseContentState() }
    fun pause() { work?.cancel(); mutableState.value = state.value.copy(loading = false) }
    suspend fun stopAndClear() { val previous = work; close(); previous?.cancelAndJoin() }

    fun resume() {
        mutableState.value = state.value.copy(nowEpoch = clock.instant().epochSecond)
        val updated = if (state.value.folder == null) state.value.sectionsUpdatedEpoch else state.value.filesUpdatedEpoch
        if (SyncCoordinator.isStale(updated, clock.instant().epochSecond)) refresh()
    }

    fun refresh() {
        val current = state.value
        val course = current.course ?: return
        if (current.loading) return
        mutableState.value = current.copy(loading = true, error = null, nowEpoch = clock.instant().epochSecond)
        work = viewModelScope.launch {
            try {
                val result = if (current.folder == null) {
                    withContext(Dispatchers.IO) { repository.sections(course.remoteId) }.map {
                        val epoch = clock.instant().epochSecond
                        state.value.copy(sections = it, sectionsUpdatedEpoch = epoch, nowEpoch = epoch, needsLogin = false)
                    }
                } else {
                    withContext(Dispatchers.IO) { repository.files(course.remoteId, current.folder.remoteId) }.map {
                        val epoch = clock.instant().epochSecond
                        state.value.copy(files = it, filesUpdatedEpoch = epoch, nowEpoch = epoch, needsLogin = false)
                    }
                }
                // Cancellation prevents a previous navigation/account's response from becoming current content.
                if (result.isSuccess) {
                    mutableState.value = result.getOrThrow()
                    AppLog.record(AppLog.Event.CONTENT_LOADED)
                } else {
                    val reason = (result.exceptionOrNull() as? PortalException)?.reason ?: PortalException.Reason.PARSE_ERROR
                    mutableState.value = state.value.copy(error = reason,
                        needsLogin = repository.session.value == ProviderSession.State.NeedsLogin)
                    AppLog.record(AppLog.Event.CONTENT_LOAD_FAILED, result.exceptionOrNull())
                }
            } finally { if (isActive) mutableState.value = state.value.copy(loading = false) }
        }
    }
}
