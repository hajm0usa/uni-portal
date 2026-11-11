package ir.hajmousa.uniportal.feature

import android.view.WindowManager
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.auth.LoginCredentials
import ir.hajmousa.uniportal.auth.SsoSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SavedLoginScreen(
    session: SsoSession,
    modifier: Modifier = Modifier,
    onCredentialsChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    // Passwords live only in memory and encrypted storage, never in saved-instance state.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf<LoginCredentials?>(null) }
    var busy by remember { mutableStateOf(true) }
    var prepared by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val wasSecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        val previousAutofill = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            view.importantForAutofill = previousAutofill
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(session) {
        val stored = withContext(Dispatchers.IO) { session.credentials.read() }
        credentials = stored.getOrNull()
        storageError = stored.isFailure
        prepared = session.clearCookies()
        storageError = storageError || !prepared
        webLogin = credentials != null && prepared
        busy = false
    }

    if (webLogin) {
        SsoLoginScreen(
            session, modifier, credentials, attempted,
            onAttempted = { attempted = true },
            onEditCredentials = {
                credentials = null
                webLogin = false
                busy = true
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) { session.credentials.clear() }
                    storageError = cleared.isFailure
                    if (cleared.isSuccess) onCredentialsChanged(false)
                    busy = false
                }
            },
            onBack = onBack,
            onResult = {
                credentials = null
                onResult(it)
            },
        )
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.login_title))
            Text(stringResource(R.string.credentials_explanation))
            if (busy) CircularProgressIndicator()
            if (storageError) Text(stringResource(R.string.credentials_storage_error))
            OutlinedTextField(
                value = username, onValueChange = { if (it.length <= 1024) username = it },
                label = { Text(stringResource(R.string.credentials_username)) },
                singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { if (it.length <= 4096) password = it },
                label = { Text(stringResource(R.string.credentials_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && prepared && username.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    val entered = LoginCredentials(username.trim(), password)
                    password = ""
                    busy = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { session.credentials.save(entered) }
                        storageError = saved.isFailure
                        if (saved.isSuccess) {
                            onCredentialsChanged(true)
                            credentials = entered
                            attempted = false
                            // A user may have manually logged in before returning to this form.
                            prepared = session.clearCookies()
                            storageError = !prepared
                            webLogin = prepared
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.credentials_save_login)) }
            TextButton(enabled = !busy && prepared, onClick = {
                busy = true
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) { session.credentials.clear() }
                    storageError = cleared.isFailure
                    if (cleared.isSuccess) {
                        credentials = null
                        onCredentialsChanged(false)
                        prepared = session.clearCookies()
                        storageError = !prepared
                        webLogin = prepared
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.credentials_manual_login)) }
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) { session.credentials.clear() }
                    storageError = cleared.isFailure
                    if (cleared.isSuccess) {
                        credentials = null
                        password = ""
                        username = ""
                        onCredentialsChanged(false)
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.credentials_forget)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.login_cancel)) }
        }
    }
}
