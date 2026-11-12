package ir.hajmousa.uniportal.feature

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.data.DownloadFolderStore
import kotlinx.coroutines.awaitCancellation

@Composable
internal fun DownloadFolderCard() {
    val context = LocalContext.current
    val model = viewModel<DownloadFolderViewModel> { DownloadFolderViewModel(DownloadFolderStore(context)) }
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), model::select)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh(); awaitCancellation() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.download_folder_title))
            Text(stringResource(if (state.selected) R.string.download_folder_ready else R.string.download_folder_missing))
            Text(stringResource(R.string.download_folder_restrictions))
            if (state.error) Text(stringResource(R.string.download_folder_error))
            Button(enabled = !state.loading, onClick = {
                try { picker.launch(null) }
                catch (_: ActivityNotFoundException) { model.pickerFailed() }
                catch (_: SecurityException) { model.pickerFailed() }
            }) { Text(stringResource(if (state.selected) R.string.download_folder_change else R.string.download_folder_choose)) }
        }
    }
}
