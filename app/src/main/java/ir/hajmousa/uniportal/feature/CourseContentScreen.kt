package ir.hajmousa.uniportal.feature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CourseActivity
import ir.hajmousa.uniportal.data.CourseContentRepository
import ir.hajmousa.uniportal.data.CourseSection
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.feature.theme.PortalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseContentScreen(
    state: CourseContentState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFolder: (CourseActivity) -> Unit,
    onSignIn: () -> Unit,
) {
    val course = state.course ?: return
    val context = LocalContext.current
    var browserFailed by remember(course.remoteId, state.folder?.remoteId) { mutableStateOf(false) }
    val openBrowser: (String) -> Unit = { value ->
        val url = CourseContentRepository.safeBrowserUrl(value)
        browserFailed = if (url == null) true else try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            false
        } catch (_: ActivityNotFoundException) { true } catch (_: SecurityException) { true }
    }
    val fallback = state.folder?.deepLink ?: CourseContentRepository.courseBrowserUrl(course.remoteId)
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = { if (!state.needsLogin) onRefresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.logs_back)) }
                Text(state.folder?.title ?: course.name ?: course.shortName ?: stringResource(R.string.course_unnamed),
                    style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.content_cache_note))
                val updated = if (state.folder == null) state.sectionsUpdatedEpoch else state.filesUpdatedEpoch
                updated?.let { Text(stringResource(R.string.courses_last_updated, formatPersianDateTime(it))) }
                if (updated != null && (state.error != null || SyncCoordinator.isStale(updated, state.nowEpoch))) {
                    Text(stringResource(R.string.content_stale_note))
                }
                if (state.loading) CircularProgressIndicator()
                if (state.needsLogin) {
                    Text(stringResource(R.string.courses_needs_login))
                    Button(onClick = onSignIn) { Text(stringResource(R.string.login_open)) }
                } else Button(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.logs_refresh)) }
                if (fallback != null) TextButton(onClick = { openBrowser(fallback) }) { Text(stringResource(R.string.content_browser)) }
                Text(stringResource(R.string.content_browser_login_note), style = MaterialTheme.typography.bodySmall)
                if (browserFailed) Text(stringResource(R.string.content_browser_failed))
            }
            state.error?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED }?.let { error ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(stringResource(when (error) {
                            PortalException.Reason.NETWORK_ERROR -> R.string.courses_network_error
                            PortalException.Reason.STORAGE_ERROR -> R.string.courses_storage_error
                            PortalException.Reason.PARSE_ERROR -> R.string.courses_parse_error
                            else -> R.string.content_server_error
                        }), Modifier.padding(16.dp))
                    }
                }
            }
            if (state.folder == null) {
                val sections = state.sections
                if (sections != null && sections.isEmpty()) item { Text(stringResource(R.string.content_empty)) }
                sections?.forEach { section ->
                    item(key = "section:${section.remoteId}") { Text(section.title, style = MaterialTheme.typography.titleLarge) }
                    if (section.activities.isEmpty()) item { Text(stringResource(R.string.content_section_empty)) }
                    items(section.activities, key = { "activity:${it.remoteId}" }) { activity ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(activity.title, style = MaterialTheme.typography.titleMedium)
                                when (activity.kind) {
                                    CourseActivity.Kind.LABEL -> Unit
                                    CourseActivity.Kind.FOLDER -> Button(onClick = { onFolder(activity) },
                                        enabled = !state.loading && activity.deepLink != null) {
                                        Text(stringResource(R.string.content_open_folder))
                                    }
                                    else -> {
                                        Text(stringResource(R.string.content_browser_only))
                                        activity.deepLink?.let { link ->
                                            TextButton(onClick = { openBrowser(link) }) { Text(stringResource(R.string.content_browser)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item { DownloadFolderCard() }
                item { Text(stringResource(R.string.content_downloads_pending)) }
                val files = state.files
                if (files != null && files.isEmpty()) item { Text(stringResource(R.string.content_folder_empty)) }
                items(files.orEmpty(), key = { it.remoteId }) { file ->
                    Card(Modifier.fillMaxWidth()) { Text(file.name, Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

class CourseContentPreviewProvider : PreviewParameterProvider<CourseContentState> {
    private val course = Course("moodle", "1", "درس نمونه", null)
    private val sections = listOf(CourseSection("moodle", "100", "1", "بخش نمونه", emptyList()))
    override val values = sequenceOf(
        CourseContentState(course = course, loading = true),
        CourseContentState(course = course, sections = emptyList()),
        CourseContentState(course = course, sections = sections, sectionsUpdatedEpoch = 1700000000),
        CourseContentState(course = course, sections = sections, error = PortalException.Reason.NETWORK_ERROR),
        CourseContentState(course = course, needsLogin = true),
    )
}

@Preview(locale = "fa", showBackground = true)
@Composable
private fun ContentPreview(@PreviewParameter(CourseContentPreviewProvider::class) state: CourseContentState) {
    PortalTheme { CourseContentScreen(state, onBack = {}, onRefresh = {}, onFolder = {}, onSignIn = {}) }
}
