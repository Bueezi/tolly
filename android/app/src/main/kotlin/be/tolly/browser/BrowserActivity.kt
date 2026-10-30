package be.tolly.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.TollyActivity
import be.tolly.core.auth.SessionManager
import be.tolly.core.auth.handleWebViewLink
import be.tolly.core.auth.mobileUserAgent
import be.tolly.core.designsystem.R
import be.tolly.core.ui.openExternally
import javax.inject.Inject

/**
 * In-app browser for the Toledo pages Tolly doesn't show natively (tools, tests, assignments).
 * It borrows the app's session for as long as it is open, so the user doesn't have to sign in again.
 */
@AndroidEntryPoint
class BrowserActivity : TollyActivity() {
    @Inject lateinit var sessionManager: SessionManager

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingFileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pendingFileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.dataString
        if (url == null) {
            finish()
            return
        }
        sessionManager.shareSessionWithWebView()
        setThemedContent {
            BrowserScreen(
                startUrl = url,
                onClose = ::finish,
                onChooseFiles = ::chooseFiles,
                onDownload = ::download,
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) sessionManager.reclaimSessionFromWebView()
        super.onDestroy()
    }

    private fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        return try {
            pickFiles.launch(params.createIntent())
            true
        } catch (_: Exception) {
            pendingFileCallback = null
            false
        }
    }

    /** Files the page offers go to Downloads/Tolly through the system download manager, with the session cookie. */
    private fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            openExternally(url) // writing to the public Downloads folder needs a permission before Android 10
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .addRequestHeader("User-Agent", userAgent)
            .setMimeType(mimeType)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Tolly/$fileName")
        getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(this, getString(R.string.browser_downloading, fileName), Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    startUrl: String,
    onClose: () -> Unit,
    onChooseFiles: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    onDownload: (url: String, userAgent: String, contentDisposition: String?, mimeType: String?) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    // Each renderer the view loses to Android taking its memory gets a new view on the same page.
    var renderer by remember { mutableIntStateOf(0) }
    val host = remember(currentUrl) { Uri.parse(currentUrl).host.orEmpty() }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title.ifBlank { host }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                host,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_reload))
                        }
                        val context = LocalContext.current
                        IconButton(onClick = { context.openExternally(currentUrl) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.action_open_in_browser))
                        }
                    },
                )
                if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        key(renderer) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
                factory = { context ->
                    WebView(context).apply {
                        // Without these Compose gives the view WRAP_CONTENT, and WebView then lays the page
                        // out in a viewport 0px high: Ultra's panels (height: 100%, 100dvh) collapse to grey.
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        with(settings) {
                            javaScriptEnabled = true // Ultra is a JavaScript app
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(false) // target=_blank opens here
                            builtInZoomControls = true
                            displayZoomControls = false
                            // Same as the sign-in: on tablets the IdP would otherwise offer a QR code if it asks to sign in again.
                            userAgentString = mobileUserAgent(userAgentString)
                        }
                        // Embedded tools (e.g. lecture recordings) run on other hosts and need their cookies.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView, newTitle: String?) {
                                title = newTitle.orEmpty()
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                callback: ValueCallback<Array<Uri>>,
                                params: FileChooserParams,
                            ): Boolean = onChooseFiles(callback, params)
                        }
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                url?.let { currentUrl = it }
                                canGoBack = view.canGoBack()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                view.context.handleWebViewLink(request.url)

                            // Not handling this takes the whole app down with the renderer. A page that
                            // crashed it would only crash it again, so then the browser closes.
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                if (detail.didCrash()) onClose() else renderer++
                                return true
                            }
                        }
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            onDownload(url, userAgent, contentDisposition, mimeType)
                        }
                        // Load after the first layout: pages sized with vh units collapse when loaded at 0x0.
                        doOnLayout { loadUrl(currentUrl) }
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
