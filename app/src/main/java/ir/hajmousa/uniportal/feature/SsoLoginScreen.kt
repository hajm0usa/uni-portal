package ir.hajmousa.uniportal.feature

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.auth.SsoSession
import ir.hajmousa.uniportal.auth.LoginCredentials
import ir.hajmousa.uniportal.auth.AutomaticSsoLogin
import ir.hajmousa.uniportal.core.AppLog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SsoLoginScreen(
    session: SsoSession,
    modifier: Modifier = Modifier,
    credentials: LoginCredentials? = null,
    attempted: Boolean = false,
    onAttempted: () -> Unit = {},
    onEditCredentials: () -> Unit,
    onBack: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resultCallback = rememberUpdatedState(onResult)
    val attemptCallback = rememberUpdatedState(onAttempted)
    val active = remember { AtomicBoolean(true) }
    val automation = remember { AutomaticSsoLogin(context.assets.open("sso-login.js").bufferedReader().use { it.readText() }, attempted) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) {
        onDispose {
            active.set(false)
            executor.shutdownNow()
        }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (credentials != null) Text(
            stringResource(R.string.credentials_automatic_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TextButton(onClick = onEditCredentials) { Text(stringResource(R.string.credentials_edit)) }
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { viewContext ->
                createLoginWebView(viewContext, session, executor, credentials, automation,
                    isActive = { active.get() },
                    canSubmit = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
                    onAttempted = { attemptCallback.value() },
                    onResult = { if (active.get()) resultCallback.value(it) },
                ).apply {
                    loadUrl(SsoHosts.LOGIN_URL)
                }
            },
            onReset = null,
            onRelease = {
                active.set(false)
                it.stopLoading()
                it.webViewClient = WebViewClient()
                it.destroy()
            },
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.login_cancel))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun createLoginWebView(
    context: android.content.Context,
    session: SsoSession,
    executor: java.util.concurrent.ExecutorService,
    credentials: LoginCredentials?,
    automation: AutomaticSsoLogin,
    isActive: () -> Boolean,
    canSubmit: () -> Boolean,
    onAttempted: () -> Unit,
    onResult: (Boolean) -> Unit,
): WebView {
    WebView.setWebContentsDebuggingEnabled(false)
    val completed = AtomicBoolean(false)
    var callbackSeen = false
    val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.safeBrowsingEnabled = true
        settings.saveFormData = false
        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        cookies.setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (request.isForMainFrame && isCallback(uri.toString())) callbackSeen = true
                return handleNavigation(context, uri, request.isForMainFrame)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (isCallback(url)) callbackSeen = true
                return handleNavigation(context, Uri.parse(url), true)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!isActive()) return
                if (credentials != null && !completed.get() && canSubmit()) {
                    automation.probe(url)?.let { probe ->
                        view.evaluateJavascript(probe) { status ->
                            if (!isActive() || completed.get() || !canSubmit()) return@evaluateJavascript
                            if (status == "\"ready\"") {
                                automation.submit(view.url ?: "", credentials)?.let { script ->
                                    onAttempted()
                                    view.evaluateJavascript(script, null)
                                }
                            } else {
                                automation.stop()
                                onAttempted()
                            }
                        }
                    }
                }
                if (!callbackSeen || isCallback(url) || !isMoodlePage(url) || !completed.compareAndSet(false, true)) return
                executor.execute {
                    if (!isActive()) return@execute
                    val active = try {
                        cookies.flush()
                        session.importWebViewCookies(cookies, isCurrent = isActive)
                    } catch (_: Exception) {
                        AppLog.record(AppLog.Event.COOKIE_STORAGE_FAILED)
                        false
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(active) }
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                automation.stop()
                if (isActive()) onAttempted()
                handler.cancel()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    automation.stop()
                    if (isActive()) onAttempted()
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    automation.stop()
                    if (isActive()) onAttempted()
                }
            }
        }
    }
}

private fun handleNavigation(context: android.content.Context, uri: Uri, mainFrame: Boolean): Boolean {
    if (isAllowedWebViewUrl(uri.toString())) return false
    if (!mainFrame) return true
    val externalUri = runCatching { URI(uri.toString()) }.getOrNull() ?: return true
    if (!SsoHosts.isExternalBrowserUrl(externalUri)) return true
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        true
    }
}

internal fun isAllowedWebViewUrl(value: String): Boolean = runCatching {
    SsoHosts.isAllowed(URI(value))
}.getOrDefault(false)

private fun isCallback(value: String): Boolean = runCatching {
    SsoHosts.isCallback(URI(value))
}.getOrDefault(false)

private fun isMoodlePage(value: String): Boolean = runCatching {
    URI(value).host?.equals(SsoHosts.MOODLE_HOST, ignoreCase = true) == true &&
        URI(value).scheme.equals("https", ignoreCase = true)
}.getOrDefault(false)
