package ir.hajmousa.uniportal.feature

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hajmousa.uniportal.data.DownloadFolderStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadFolderState(val loading: Boolean = true, val selected: Boolean = false, val error: Boolean = false)

class DownloadFolderViewModel(private val store: DownloadFolderStore) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadFolderState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    fun refresh() {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            val result = store.current()
            mutableState.value = DownloadFolderState(loading = false, selected = result.getOrNull() != null, error = result.isFailure)
        }
    }

    fun select(uri: Uri?) {
        if (uri == null) return
        work?.cancel()
        mutableState.value = state.value.copy(loading = true, error = false)
        work = viewModelScope.launch {
            val saved = store.select(uri)
            val current = store.current()
            mutableState.value = DownloadFolderState(loading = false, selected = current.getOrNull() != null,
                error = saved.isFailure || current.isFailure)
        }
    }

    fun pickerFailed() { mutableState.value = state.value.copy(error = true) }
}
