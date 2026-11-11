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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.hajmousa.uniportal.BuildConfig
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.auth.SsoSession
import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.data.createSyncCoordinator
import ir.hajmousa.uniportal.data.SyncCoordinator
import ir.hajmousa.uniportal.data.Course
import ir.hajmousa.uniportal.data.CachedCoursePage
import ir.hajmousa.uniportal.data.PortalDatabase
import ir.hajmousa.uniportal.core.time.formatPersianDateTime
import ir.hajmousa.uniportal.feature.theme.PortalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation

private enum class PortalTab(val title: Int, val icon: Int) {
    TODAY(R.string.tab_today, R.drawable.nav_today),
    COURSES(R.string.courses_title, R.drawable.nav_courses),
    CALENDAR(R.string.tab_calendar, R.drawable.nav_calendar),
    GRADES(R.string.tab_grades, R.drawable.nav_grades),
    MESSAGES(R.string.tab_messages, R.drawable.nav_messages),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalApp() {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var loginFailed by rememberSaveable { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var hasSavedCredentials by remember { mutableStateOf(false) }
    var credentialStorageError by remember { mutableStateOf(false) }
    var automaticLoginUsed by rememberSaveable { mutableStateOf(false) }
    var signedOut by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(PortalTab.TODAY) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val session = remember(context) { SsoSession(context) }
    val model = viewModel<PortalViewModel> {
        PortalViewModel(createSyncCoordinator(
            session.cookieJar, "UniPortal/${BuildConfig.VERSION_NAME} (Android; read-only)",
            PortalDatabase.get(context),
        ))
    }
    val state by model.state.collectAsStateWithLifecycle()
    val coursesState = CoursesState(page = state.courses, loading = state.loading, clearing = state.clearing,
        needsLogin = state.needsLogin, error = state.coursesError?.takeUnless { it == PortalException.Reason.SESSION_EXPIRED },
        fromCache = state.courses != null && (state.coursesError != null || SyncCoordinator.isStale(state.courses?.lastSuccessEpochSeconds, state.nowEpoch)))
    val loading = state.loading || state.clearing
    val needsLogin = state.needsLogin
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(session) {
        val stored = withContext(Dispatchers.IO) { session.credentials.read().map { it != null } }
        hasSavedCredentials = stored.getOrDefault(false)
        credentialStorageError = stored.isFailure
    }
    LaunchedEffect(lifecycle, showLogin, showLogs, signingOut, signedOut) {
        if (!showLogin && !showLogs && !signingOut && !signedOut) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    model.state.first { !it.loading && !it.clearing }
                    model.refresh()
                    awaitCancellation()
                } finally { model.pauseRefresh() }
            }
        }
    }
    LaunchedEffect(needsLogin, hasSavedCredentials, loading, showLogin, signingOut, showLogs, foreground) {
        if (foreground && needsLogin && hasSavedCredentials && !automaticLoginUsed && !loading && !showLogin && !signingOut && !showLogs) {
            automaticLoginUsed = true
            model.clearForAccountChange {
                signedIn = false
                loginFailed = false
                showLogin = true
            }
        }
    }
    LaunchedEffect(state) {
        if (state.authenticated && !loading && !signingOut) signedIn = true
        if (needsLogin) signedIn = false
    }
    BackHandler(enabled = showLogs) { showLogs = false }
    BackHandler(enabled = showLogin) { showLogin = false }

    Scaffold(bottomBar = {
        if (!showLogin && !showLogs) NavigationBar {
            PortalTab.entries.forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = { tab = item },
                    icon = { Icon(painterResource(item.icon), contentDescription = null) },
                    label = { Text(stringResource(item.title)) })
            }
        }
    }) { padding ->
        if (showLogs) {
            LogScreen(Modifier.padding(padding)) { showLogs = false }
        } else if (showLogin) {
            SavedLoginScreen(session, Modifier.padding(padding),
                onCredentialsChanged = {
                    hasSavedCredentials = it
                    credentialStorageError = false
                },
                onBack = { showLogin = false },
            ) { success ->
                showLogin = false
                signedIn = success
                loginFailed = !success
                if (success) {
                    signedOut = false
                    model.refresh(force = true)
                }
            }
        } else {
            val scroll = rememberScrollState()
            LaunchedEffect(tab) { scroll.scrollTo(0) }
            PullToRefreshBox(
                isRefreshing = state.loading && !state.clearing,
                onRefresh = { if (!signingOut && !loading && !needsLogin) model.refresh(force = true) },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp),
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
                        if (credentialStorageError) Text(stringResource(R.string.credentials_storage_error))
                        if (signedIn || coursesState.page != null || state.agenda != null || hasSavedCredentials || credentialStorageError) {
                            TextButton(enabled = !loading && !signingOut, onClick = {
                                automaticLoginUsed = true
                                scope.launch {
                                    // Finish local deletion even if the UI is recreated during logout.
                                    withContext(NonCancellable) {
                                        signingOut = true
                                        signedOut = true
                                        model.stopRefresh()
                                        val cleared = session.signOut()
                                        signedIn = false
                                        hasSavedCredentials = false
                                        credentialStorageError = !cleared
                                        model.clearForAccountChange {}
                                        signingOut = false
                                    }
                                }
                            }) { Text(stringResource(R.string.logout)) }
                        }
                        if (!signedIn || needsLogin) {
                            Button(enabled = !loading && !signingOut, onClick = {
                                automaticLoginUsed = true
                                model.clearForAccountChange {
                                    signedIn = false
                                    loginFailed = false
                                    showLogin = true
                                }
                            }) { Text(stringResource(R.string.login_open)) }
                        }
                    }
                }
                when (tab) {
                    PortalTab.COURSES -> CoursesContent(coursesState, enabled = !signingOut, onLoad = { model.refresh(force = true) })
                    PortalTab.TODAY, PortalTab.CALENDAR -> AgendaContent(state, calendar = tab == PortalTab.CALENDAR,
                        enabled = !signingOut, onRefresh = { model.refresh(force = true) })
                    PortalTab.GRADES, PortalTab.MESSAGES -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(tab.title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.coming_soon))
                        }
                    }
                }
            }
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
                        AppLog.Event.CACHE_STORAGE_FAILED -> R.string.courses_storage_error
                        AppLog.Event.CREDENTIAL_STORAGE_FAILED -> R.string.credentials_storage_error
                        AppLog.Event.AGENDA_LOADED -> R.string.log_agenda_loaded
                        AppLog.Event.AGENDA_LOAD_FAILED -> R.string.log_agenda_load_failed
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
        if (!state.needsLogin) {
            Button(onClick = onLoad, enabled = enabled && !state.loading && !state.clearing) {
                Text(stringResource(R.string.logs_refresh))
            }
        }
        if (state.loading || state.clearing) {
            CircularProgressIndicator()
            Text(stringResource(if (state.clearing) R.string.courses_clearing else R.string.courses_loading))
        }
        if (state.needsLogin) Text(stringResource(R.string.courses_needs_login))
        state.error?.let { reason ->
            Text(stringResource(when (reason) {
                PortalException.Reason.NETWORK_ERROR -> R.string.courses_network_error
                PortalException.Reason.STORAGE_ERROR -> R.string.courses_storage_error
                PortalException.Reason.PARSE_ERROR -> R.string.courses_parse_error
                else -> R.string.courses_server_error
            }))
        }
        state.page?.let { page ->
            if (state.fromCache) Text(stringResource(R.string.courses_cached_note))
            val lastUpdated = remember(page.lastSuccessEpochSeconds) { formatPersianDateTime(page.lastSuccessEpochSeconds) }
            Text(stringResource(R.string.courses_last_updated, lastUpdated))
            Text(stringResource(
                R.string.courses_count,
                java.text.NumberFormat.getInstance(java.util.Locale("fa")).format(page.courses.size),
            ))
            if (page.courses.isEmpty()) Text(stringResource(R.string.courses_empty))
            page.courses.forEach { course ->
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

@Preview(locale = "fa", showBackground = true)
@Composable
private fun PortalPreview() {
    PortalTheme { PortalApp() }
}

class CoursesPreviewProvider : PreviewParameterProvider<CoursesState> {
    override val values = sequenceOf(
        CoursesState(),
        CoursesState(loading = true),
        CoursesState(page = CachedCoursePage(emptyList(), 1700000000)),
        CoursesState(page = CachedCoursePage(listOf(Course("moodle", "1", null, null)), 1700000000), fromCache = true),
        CoursesState(needsLogin = true),
        CoursesState(error = PortalException.Reason.NETWORK_ERROR),
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
