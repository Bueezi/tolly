package be.tolly.feature.login

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.auth.configureForLogin
import be.tolly.core.auth.handleWebViewLink
import be.tolly.core.data.DataError
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import java.net.URI
import java.util.UUID

/**
 * KU Leuven sign-in inside a WebView. The user types credentials into KU Leuven's own page; the app
 * only picks up the resulting Ultra session cookies once the browser lands back on Ultra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onClose: () -> Unit,
    // A fresh ViewModel per login attempt, so an earlier finished login is never reused.
    viewModel: LoginViewModel = hiltViewModel(key = rememberSaveable { "login-" + UUID.randomUUID() }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var progress by remember { mutableFloatStateOf(0f) }
    var host by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(state) {
        if (state == LoginState.Done) onSignedIn()
    }

    BackHandler {
        val view = webView
        if (state == LoginState.Browsing && view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.login_title), maxLines = 1)
                            host?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.login_secure_connection),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                )
                if (state == LoginState.Browsing && progress < 1f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                LoginState.Browsing -> LoginWebView(
                    onCreated = { webView = it },
                    onProgress = { progress = it },
                    onUrl = { url -> host = runCatching { URI(url).host }.getOrNull() },
                    onUltraReached = viewModel::onUltraReached,
                )
                LoginState.Verifying, LoginState.Done -> LoadingState()
                is LoginState.Failed -> MessageState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.login_failed_title),
                    body = stringResource(
                        if (current.error == DataError.OFFLINE) R.string.error_offline else R.string.login_failed_body,
                    ),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = viewModel::retry,
                )
            }
        }
    }
}

@Composable
private fun LoginWebView(
    onCreated: (WebView) -> Unit,
    onProgress: (Float) -> Unit,
    onUrl: (String) -> Unit,
    onUltraReached: () -> Unit,
) {
    // A renderer the view loses (Android took its memory, or it crashed) gets a new view, which
    // starts the sign-in again.
    var renderer by remember { mutableIntStateOf(0) }
    key(renderer) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
                WebView(context).apply {
                    configureForLogin()
                    if (debuggable) Log.d(TAG, "user agent ${settings.userAgentString}")
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress / 100f)

                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (debuggable) Log.d(TAG, "console ${message.messageLevel()}: ${message.message()}")
                            return super.onConsoleMessage(message)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            if (debuggable) Log.d(TAG, "page started ${url?.substringBefore('?')}")
                            url?.let(onUrl)
                            if (UltraEndpoints.isLoginComplete(url)) {
                                view.stopLoading()
                                onUltraReached()
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (debuggable) Log.d(TAG, "page finished ${url?.substringBefore('?')}")
                            if (UltraEndpoints.isLoginComplete(url)) onUltraReached()
                            if (UltraEndpoints.isIdentityProvider(url)) view.evaluateJavascript(CHECK_REMEMBER_DEVICE, null)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (debuggable) Log.w(TAG, "load error ${error.errorCode} ${error.description} for ${request.url.toString().substringBefore('?')}")
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (debuggable) Log.w(TAG, "http ${response.statusCode} for ${request.url.toString().substringBefore('?')}")
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val uri = request.url
                            if (debuggable) Log.d(TAG, "navigate ${uri.scheme}://${uri.host}${uri.path}")
                            return view.context.handleWebViewLink(uri)
                        }

                        // Not handling this takes the whole app down with the renderer.
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            renderer++
                            return true
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    onCreated(this)
                    // Load only once the view has its real size: recent WebView versions fix CSS `vh`
                    // to the size at load time, and the IdP's full-height layout would collapse to 0px.
                    doOnLayout { loadUrl(UltraEndpoints.LOGIN_URL) }
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

private const val TAG = "TollyLogin"

/**
 * Ticks the IdP's "remember my device" box, so the second factor isn't asked at every sign-in.
 * The box is found by what it says (the IdP's markup isn't ours), also when the page builds it
 * after loading; a box the user unticks stays unticked. Nothing else on the page is touched.
 */
private const val CHECK_REMEMBER_DEVICE = """
(function () {
  if (window.__tollyRemember) return;
  window.__tollyRemember = true;
  var wanted = /remember|onthoud|bewaar|trust|vertrouw|this device|dit apparaat|dit toestel|apparaat onthouden/i;
  function describe(box) {
    var text = [box.id, box.name, box.getAttribute('aria-label'), box.value].join(' ');
    if (box.id) {
      var label = document.querySelector('label[for="' + box.id.replace(/"/g, '') + '"]');
      if (label) text += ' ' + label.textContent;
    }
    var wrapper = box.closest('label') || box.parentElement;
    if (wrapper && wrapper.textContent.length < 160) text += ' ' + wrapper.textContent;
    return text;
  }
  function apply() {
    document.querySelectorAll('input[type=checkbox]').forEach(function (box) {
      if (box.checked || box.dataset.tollyAuto) return;
      if (wanted.test(describe(box))) {
        box.dataset.tollyAuto = '1';
        box.click();
      }
    });
  }
  apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
})();
"""
