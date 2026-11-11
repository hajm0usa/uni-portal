package ir.hajmousa.uniportal.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.hajmousa.uniportal.BuildConfig
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.auth.SsoSession
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.CourseRepository
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.feature.theme.PortalTheme

@Composable
fun PortalApp() {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var loginFailed by rememberSaveable { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val session = remember(context) { SsoSession(context) }
    val coursesViewModel = viewModel<CoursesViewModel> {
        CoursesViewModel(CourseRepository(session.cookieJar, "UniPortal/${BuildConfig.VERSION_NAME} (Android; read-only)"))
    }
    val coursesState by coursesViewModel.state.collectAsStateWithLifecycle()
    val loading = coursesState == CoursesState.Loading
    val needsLogin = coursesState == CoursesState.NeedsLogin
    LaunchedEffect(coursesState) {
        if (coursesState is CoursesState.Loaded) signedIn = true
        if (needsLogin) signedIn = false
    }
    BackHandler(enabled = showLogs) { showLogs = false }
    BackHandler(enabled = showLogin) { showLogin = false }

    Scaffold { padding ->
        if (showLogs) {
            LogScreen(Modifier.padding(padding)) { showLogs = false }
        } else if (showLogin) {
            SsoLoginScreen(session, Modifier.padding(padding), onBack = { showLogin = false }) { success ->
                showLogin = false
                signedIn = success
                loginFailed = !success
                if (success) coursesViewModel.load()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = {},
                        onLongClickLabel = stringResource(R.string.logs_open),
                        onLongClick = {
                            AppLog.record(AppLog.Event.LOG_SCREEN_OPENED)
                            showLogs = true
                        },
                    ).padding(vertical = 12.dp),
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.placeholder_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                needsLogin -> stringResource(R.string.courses_needs_login)
                                signedIn -> stringResource(R.string.login_success)
                                loginFailed -> stringResource(R.string.login_failed)
                                else -> stringResource(R.string.placeholder_body)
                            },
                        )
                        if (signedIn && !needsLogin) {
                            TextButton(enabled = !loading && !signingOut, onClick = {
                                signingOut = true
                                session.signOut(android.webkit.CookieManager.getInstance()) {
                                    signedIn = false
                                    coursesViewModel.reset()
                                    signingOut = false
                                }
                            }) { Text(stringResource(R.string.logout)) }
                        } else {
                            Button(enabled = !loading && !signingOut, onClick = {
                                loginFailed = false
                                coursesViewModel.reset()
                                showLogin = true
                            }) { Text(stringResource(R.string.login_open)) }
                        }
                    }
                }
                CoursesContent(coursesState, enabled = !signingOut, onLoad = coursesViewModel::load)
            }
        }
    }
}

@Composable
private fun LogScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(AppLog.snapshot()) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.logs_back)) }
            Text(stringResource(R.string.logs_title), style = MaterialTheme.typography.headlineMedium)
        }
        item { Text(stringResource(R.string.logs_privacy)) }
        item {
            TextButton(onClick = { entries = AppLog.snapshot() }) { Text(stringResource(R.string.logs_refresh)) }
            TextButton(onClick = {
                AppLog.clear()
                entries = AppLog.snapshot()
            }) { Text(stringResource(R.string.logs_clear)) }
        }
        if (entries.isEmpty()) item { Text(stringResource(R.string.logs_empty)) }
        items(entries) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val label = when (entry.event) {
                        AppLog.Event.APP_STARTED -> R.string.log_app_started
                        AppLog.Event.LOG_SCREEN_OPENED -> R.string.log_screen_opened
                        AppLog.Event.COOKIE_STORAGE_FAILED -> R.string.log_cookie_storage_failed
                        AppLog.Event.COURSES_LOADED -> R.string.log_courses_loaded
                        AppLog.Event.COURSE_LOAD_FAILED -> R.string.log_course_load_failed
                        AppLog.Event.SESSION_EXPIRED -> R.string.courses_needs_login
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                    entry.diagnostic?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursesContent(state: CoursesState, enabled: Boolean = true, onLoad: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.courses_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.courses_first_page_note))
        if (state != CoursesState.NeedsLogin) {
            Button(onClick = onLoad, enabled = enabled && state != CoursesState.Loading) {
                Text(stringResource(R.string.courses_load))
            }
        }
        when (state) {
            CoursesState.Idle -> Unit
            CoursesState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.courses_loading))
            }
            CoursesState.NeedsLogin -> Text(stringResource(R.string.courses_needs_login))
            is CoursesState.Failed -> Text(stringResource(when (state.reason) {
                PortalException.Reason.NETWORK_ERROR -> R.string.courses_network_error
                PortalException.Reason.STORAGE_ERROR -> R.string.log_cookie_storage_failed
                PortalException.Reason.PARSE_ERROR -> R.string.courses_parse_error
                else -> R.string.courses_server_error
            }))
            is CoursesState.Loaded -> {
                Text(stringResource(
                    R.string.courses_count,
                    java.text.NumberFormat.getInstance(java.util.Locale("fa")).format(state.courses.size),
                ))
                if (state.courses.isEmpty()) Text(stringResource(R.string.courses_empty))
                state.courses.forEach { course ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            course.name?.takeIf { it.isNotBlank() }
                                ?: course.shortName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.course_unnamed),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(locale = "fa", showBackground = true)
@Composable
private fun PortalPreview() {
    PortalTheme { PortalApp() }
}

class CoursesPreviewProvider : PreviewParameterProvider<CoursesState> {
    override val values = sequenceOf(
        CoursesState.Idle,
        CoursesState.Loading,
        CoursesState.Loaded(emptyList()),
        CoursesState.Loaded(listOf(Course("moodle", "1", null, null))),
        CoursesState.NeedsLogin,
        CoursesState.Failed(PortalException.Reason.NETWORK_ERROR),
    )
}

@Preview(locale = "fa", showBackground = true)
@Composable
private fun CoursesPreview(@PreviewParameter(CoursesPreviewProvider::class) state: CoursesState) {
    PortalTheme { CoursesContent(state, onLoad = {}) }
}

@Preview(locale = "fa", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogsPreview() {
    PortalTheme { LogScreen(onBack = {}) }
}
