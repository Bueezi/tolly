package be.tolly.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import be.tolly.core.auth.MicrosoftSignIn
import be.tolly.core.auth.ToledoPortal
import be.tolly.core.auth.handleWebViewLink
import be.tolly.core.auth.mobileUserAgent
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.ui.MailDraft
import be.tolly.core.ui.openExternally
import org.json.JSONObject

/**
 * Outlook on the web, for the KU Leuven mailbox. Microsoft's sign-in hands over to the KU Leuven
 * one, which the WebView usually still holds, so it often opens signed in. The page is kept while
 * the user is on other tabs (see [MailWebView]), so coming back doesn't load Outlook again.
 */
@Composable
internal fun MailScreen(mail: MailWebView, unread: Int?, email: String?) {
    val context = LocalContext.current
    val webView = mail.get(context, email)
    BackHandler(enabled = mail.canGoBack) { webView.goBack() }
    TollyScaffold(
        recordContent = false,
        topBar = {
            // A large title like the other tabs'. Outlook scrolls inside the WebView, so it stays open.
            TollyTopBar(
                title = { Text(stringResource(R.string.nav_mail)) },
                refreshing = mail.loading && mail.shown,
                subtitle = if (unread != null && unread > 0) {
                    { Text(pluralStringResource(R.plurals.mail_unread, unread, unread)) }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { context.openExternally(webView.url ?: ToledoPortal.OUTLOOK_URL) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.video_open_in_browser))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(bottom = LocalBottomBarInset.current)) {
            AndroidView(
                factory = {
                    // The same view each time the tab is shown: it has to leave the previous one first.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Outlook's sign-in hops through a few pages before it draws anything: until then the
            // view is blank white.
            if (!mail.shown) LoadingState(Modifier.fillMaxSize().background(Tolly.colors.screen))
        }
    }
}

/** The Mail tab's WebView, made once and kept for as long as the app's screen is. */
internal class MailWebView {
    private var view: WebView? = null
    var canGoBack by mutableStateOf(false)
        private set

    /** Whether a page is loading, for the top bar's progress. */
    var loading by mutableStateOf(true)
        private set

    /** Whether a page has drawn yet; before that the screen shows a loading state over the view. */
    var shown by mutableStateOf(false)
        private set

    /**
     * The view, made the first time. Outlook opens with the mail domain of the user's [email] as its
     * realm, so Microsoft goes straight on to the KU Leuven sign-in instead of asking for an address.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun get(context: Context, email: String?): WebView = view ?: WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        with(settings) {
            javaScriptEnabled = true // Outlook is a web app
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = mobileUserAgent(userAgentString)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                shown = true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (MicrosoftSignIn.isSignIn(url)) view.evaluateJavascript(MicrosoftSignIn.STAY_SIGNED_IN, null)
                // Outlook itself, after any sign-in on the way: the new-mail form can be filled in.
                val draft = pendingDraft
                if (draft != null && url != null && url.startsWith(ToledoPortal.OUTLOOK_URL)) {
                    pendingDraft = null
                    view.evaluateJavascript(fillDraftScript(draft), null)
                }
            }

            // The Authenticator, mailto: and other apps' links leave the WebView.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                view.context.handleWebViewLink(request.url)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loading = newProgress < 100
            }
        }
        val realm = email?.substringAfter('@', "")?.takeIf { it.isNotBlank() } ?: DEFAULT_REALM
        val first = pendingUrl ?: ToledoPortal.OUTLOOK_URL
        pendingUrl = null
        loadUrl(Uri.parse(first).buildUpon().appendQueryParameter("realm", realm).build().toString())
        view = this
    }

    /** A page to open instead of the inbox when the view is made, set by [compose]. */
    private var pendingUrl: String? = null

    /** The mail to fill in once Outlook's new-mail form has loaded. */
    private var pendingDraft: MailDraft? = null

    /**
     * Opens Outlook's new-mail form and fills in [draft]; the view is made for it if the Mail tab
     * wasn't opened yet. Outlook on a phone ignores its compose links' fields (they land in the
     * inbox), so the form is opened by its own address and filled in like the user would.
     */
    fun compose(context: Context, email: String?, draft: MailDraft) {
        pendingDraft = draft
        val url = ToledoPortal.OUTLOOK_URL + COMPOSE_PATH
        val existing = view
        if (existing != null) {
            existing.loadUrl(url)
        } else {
            pendingUrl = url
            get(context, email)
        }
    }

    private companion object {
        const val DEFAULT_REALM = "kuleuven.be"
        const val COMPOSE_PATH = "inbox/compose/new"
    }

    fun destroy() {
        view?.destroy()
        view = null
    }
}

/**
 * Fills in Outlook's new-mail form with [draft], typing into its fields (Outlook's editor only
 * notices typed text) and attaching the file through the form's own file field. Waits up to 20
 * seconds for the form; nothing is sent, the user reads the mail over and sends it.
 */
private fun fillDraftScript(draft: MailDraft): String {
    fun js(value: String?) = if (value == null) "null" else JSONObject.quote(value)
    return """
(function () {
  if (window.__tollyDraft) return;
  window.__tollyDraft = true;
  var draft = { to: ${js(draft.to)}, subject: ${js(draft.subject)}, body: ${js(draft.body)},
                fileName: ${js(draft.attachmentName)}, file: ${js(draft.attachment)} };
  var tries = 0;
  function type(element, text) {
    element.focus();
    document.execCommand('insertText', false, text);
  }
  function fill() {
    var to = document.querySelector('input[type=email]');
    var subject = document.querySelector('input[type=text][placeholder]');
    var body = document.getElementById('editorDiv');
    if (!to || !subject || !body) {
      if (++tries < 80) setTimeout(fill, 250); else window.__tollyDraft = false;
      return;
    }
    type(to, draft.to + ';');
    type(subject, draft.subject);
    type(body, draft.body);
    if (draft.file != null) setTimeout(function () { attach(0); }, 1500); else window.__tollyDraft = false;
  }
  // The form takes the file only once it has settled; the file's name in the form shows it did.
  function attach(attempt) {
    var shown = draft.fileName.replace(/\.txt${'$'}/, '');
    if (document.body.innerText.indexOf(shown) >= 0 || attempt >= 5) {
      window.__tollyDraft = false;
      return;
    }
    var input = document.querySelector('input[type=file]');
    if (input) {
      var files = new DataTransfer();
      files.items.add(new File([draft.file], draft.fileName, { type: 'text/plain' }));
      input.files = files.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    setTimeout(function () { attach(attempt + 1); }, 1500);
  }
  fill();
})();
"""
}
