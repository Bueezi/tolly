package be.tolly.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration

/**
 * Loads [url] in an invisible WebView and suspends until a page callback calls `finish`, or
 * [timeout] passes (null). This is for services that sign in through the KU Leuven SSO session in
 * the WebView cookie store without any UI: SSO pass-through pages submit themselves at once, so
 * an IdP page that just sits there is its login form and the run gives up with null after a few
 * seconds. [onPageFinished] returns true for a page it handles itself, which skips that check.
 */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun <T> runHiddenWebView(
    context: Context,
    url: String,
    timeout: Duration,
    onPageStarted: (url: String?, finish: (T?) -> Unit) -> Unit = { _, _ -> },
    onPageFinished: (view: WebView, url: String?, finish: (T?) -> Unit) -> Boolean,
): T? = withContext(Dispatchers.Main) {
    withTimeoutOrNull(timeout) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            webView.configureForLogin()
            var done = false

            fun finish(result: T?) {
                if (done) return
                done = true
                handler.removeCallbacksAndMessages(null)
                webView.stopLoading()
                webView.destroy()
                if (cont.isActive) cont.resume(result)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    handler.removeCallbacksAndMessages(null)
                    onPageStarted(url, ::finish)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (onPageFinished(view, url, ::finish)) return
                    if (UltraEndpoints.isIdentityProvider(url)) {
                        handler.postDelayed({ finish(null) }, IDP_SETTLE_MS)
                    }
                }

                // Never hand off to other apps (e.g. the authenticator) from the background.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme?.startsWith("http") != true
            }
            cont.invokeOnCancellation { handler.post { finish(null) } }
            webView.loadUrl(url)
        }
    }
}

private const val IDP_SETTLE_MS = 4_000L
