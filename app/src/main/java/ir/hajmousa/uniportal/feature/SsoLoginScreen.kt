package ir.hajmousa.uniportal.feature

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.hajmousa.uniportal.R
import ir.hajmousa.uniportal.auth.SsoHosts
import ir.hajmousa.uniportal.auth.SsoSession
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SsoLoginScreen(
    session: SsoSession,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) {
        onDispose { executor.shutdownNow() }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { viewContext ->
                createLoginWebView(viewContext, session, executor, onResult).apply {
                    loadUrl(SsoHosts.LOGIN_URL)
                }
            },
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.login_cancel))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createLoginWebView(
    context: android.content.Context,
    session: SsoSession,
    executor: java.util.concurrent.ExecutorService,
    onResult: (Boolean) -> Unit,
): WebView {
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
                if (!callbackSeen || isCallback(url) || !isMoodlePage(url) || !completed.compareAndSet(false, true)) return
                executor.execute {
                    cookies.flush()
                    val active = session.importWebViewCookies(cookies)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(active) }
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
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
