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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.auth.SsoSession
import ir.hajmousa.uniportal.feature.theme.PortalTheme

@Composable
fun PortalApp() {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var loginFailed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val session = remember(context) { SsoSession(context) }
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
                                signedIn -> stringResource(R.string.login_success)
                                loginFailed -> stringResource(R.string.login_failed)
                                else -> stringResource(R.string.placeholder_body)
                            },
                        )
                        if (signedIn) {
                            TextButton(onClick = {
                                session.signOut(android.webkit.CookieManager.getInstance()) {
                                    signedIn = false
                                }
                            }) { Text(stringResource(R.string.logout)) }
                        } else {
                            Button(onClick = {
                                loginFailed = false
                                showLogin = true
                            }) { Text(stringResource(R.string.login_open)) }
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

@Preview(locale = "fa", showBackground = true)
@Composable
private fun PortalPreview() {
    PortalTheme { PortalApp() }
}

@Preview(locale = "fa", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogsPreview() {
    PortalTheme { LogScreen(onBack = {}) }
}
