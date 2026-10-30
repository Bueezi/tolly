package be.tolly.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import be.tolly.core.auth.MicrosoftSignIn
import be.tolly.core.data.MailAttachment
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
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID

/**
 * Outlook on the web, for the KU Leuven mailbox. Microsoft's sign-in hands over to the KU Leuven
 * one, which the WebView usually still holds, so it often opens signed in. The page is kept while
 * the user is on other tabs (see [MailWebView]), so coming back doesn't load Outlook again; when
 * the unread count went up meanwhile, the inbox loads again to show the new mail. Outlook's attach
 * button opens Android's file picker.
 */
@Composable
internal fun MailScreen(mail: MailWebView, unread: Int?, email: String?) {
    val context = LocalContext.current
    val webView = mail.get(context, email)
    BackHandler(enabled = mail.canGoBack) { webView.goBack() }
    LaunchedEffect(unread) { mail.unreadChanged(unread) }
    // The page waits for its files until it gets an answer, even "none": without one, its attach
    // button does nothing the next time.
    val waiting = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val files = if (result.resultCode != Activity.RESULT_OK || data == null) {
            null
        } else {
            data.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } } ?: data.data?.let { arrayOf(it) }
        }
        waiting[0]?.onReceiveValue(files)
        waiting[0] = null
    }
    DisposableEffect(mail) {
        mail.chooseFiles = { intent, callback ->
            waiting[0]?.onReceiveValue(null)
            waiting[0] = callback
            try {
                picker.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                waiting[0] = null
                false
            }
        }
        onDispose {
            mail.chooseFiles = null
            waiting[0]?.onReceiveValue(null)
            waiting[0] = null
        }
    }
    TollyScaffold(
        recordContent = false,
        topBar = {
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
     * Opens the file picker for Outlook's attach button and hands what was picked to the callback;
     * false when it can't. Set while the Mail tab is on screen.
     */
    var chooseFiles: ((Intent, ValueCallback<Array<Uri>>) -> Boolean)? = null

    /** The unread count when the page last caught up with it. */
    private var knownUnread: Int? = null

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
        addJavascriptInterface(draftFiles, DraftFiles.NAME)
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
                    view.evaluateJavascript(fillDraftScript(draft, draftFiles.offer(draft.attachments)), null)
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

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                val choose = chooseFiles ?: return false
                val intent = params.createIntent()
                if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                return choose(intent, callback)
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

    private val draftFiles = DraftFiles()

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

    /**
     * Shows the inbox again, loaded afresh for mail that just came in (a new-mail notification was
     * tapped). Outlook in a background tab doesn't always catch up by itself. A mail being written
     * is left alone.
     */
    fun refreshInbox() {
        val existing = view ?: return
        if (pendingDraft != null || existing.url?.contains("/compose") == true) return
        existing.loadUrl(ToledoPortal.OUTLOOK_URL)
    }

    /** The unread count is [count] now: more than the page last saw means new mail to load. */
    fun unreadChanged(count: Int?) {
        if (count == null) return
        val before = knownUnread
        knownUnread = count
        if (before != null && count > before) refreshInbox()
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
 * notices typed text) and attaching the files through the form's own file field; the page reads
 * them through [DraftFiles], by their [keys]. Waits up to 20 seconds for the form; nothing is
 * sent, the user reads the mail over and sends it.
 *
 * The files go in once: on a slow phone their upload takes a while before Outlook lists them, and
 * putting them in again then attached them twice or more. Only those nothing shows after half a
 * minute (the form wasn't ready for them) go in a second time, and never once the form has
 * closed: sent quickly, the files went into a new draft that Outlook then opened.
 *
 * Once the form closes, Outlook may stay on an empty new-mail form instead of going back to the
 * inbox (it goes back to where the form was opened from, which is the form's own address); then
 * the inbox is opened.
 */
private fun fillDraftScript(draft: MailDraft, keys: List<String>): String {
    fun js(value: String?) = if (value == null) "null" else JSONObject.quote(value)
    val names = draft.attachments.map { it.name }
    val files = draft.attachments.zip(keys).joinToString(",\n") { (file, key) ->
        "{ name: ${js(file.name)}, mark: ${js(attachmentMark(file.name, names))}, type: ${js(file.mimeType)}, key: ${js(key)} }"
    }
    return """
(function () {
  if (window.__tollyDraft) return;
  window.__tollyDraft = true;
  var draft = { to: ${js(draft.to)}, subject: ${js(draft.subject)}, body: ${js(draft.body)} };
  var files = [$files];
  var inbox = ${js(ToledoPortal.OUTLOOK_URL)};
  var tries = 0;
  var form = null;
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
    form = body;
    type(to, draft.to + ';');
    type(subject, draft.subject);
    type(body, draft.body);
    if (draft.subject) watch(0);
    if (files.length) setTimeout(function () { attach(0); }, 1500); else window.__tollyDraft = false;
  }
  // The form filled in has gone: sent, or thrown away. Outlook also draws its editor anew at times
  // (when a file is added); with the subject still there, that is the same mail.
  function closed() {
    if (form == null || document.body.contains(form)) return false;
    var subject = document.querySelector('input[type=text][placeholder]');
    var editor = document.getElementById('editorDiv');
    if (subject && subject.value && editor) {
      form = editor;
      return false;
    }
    return true;
  }
  function watch(ticks) {
    if (!closed()) {
      if (ticks < 3600) setTimeout(function () { watch(ticks + 1); }, 1000);
    } else if (location.href.indexOf('/compose') >= 0) {
      location.replace(inbox);
    }
  }
  // The form takes a file only once it has settled; the file's name in the form shows it did
  // (Outlook may shorten the name, so a start that tells the files apart is enough).
  function attached(file) {
    var mark = file.mark.replace(/"/g, '');
    return document.body.innerText.indexOf(file.mark) >= 0 ||
      document.querySelector('[title*="' + mark + '"], [aria-label*="' + mark + '"]') != null;
  }
  function missing() {
    return files.filter(function (file) { return !attached(file); });
  }
  // The file, read a piece at a time; null when it is gone.
  function toFile(file) {
    var size = ${DraftFiles.NAME}.size(file.key);
    if (size < 0) return null;
    var bytes = new Uint8Array(size);
    for (var offset = 0; offset < size; offset += ${DraftFiles.CHUNK}) {
      var piece = atob(${DraftFiles.NAME}.read(file.key, offset));
      for (var i = 0; i < piece.length; i++) bytes[offset + i] = piece.charCodeAt(i);
    }
    return new File([bytes], file.name, { type: file.type });
  }
  function attach(attempt) {
    var left = missing();
    if (closed() || !left.length || attempt >= 2) {
      window.__tollyDraft = false;
      return;
    }
    var input = document.querySelector('input[type=file]');
    if (input) {
      var picked = new DataTransfer();
      left.forEach(function (file) {
        var made = toFile(file);
        if (made) picked.items.add(made);
      });
      input.files = picked.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
    wait(attempt, 0);
  }
  function wait(attempt, ticks) {
    if (closed() || !missing().length) {
      window.__tollyDraft = false;
    } else if (ticks < 60) {
      setTimeout(function () { wait(attempt, ticks + 1); }, 500);
    } else {
      attach(attempt + 1);
    }
  }
  fill();
})();
"""
}

/**
 * The start of [name] that shows Outlook lists the file: at least nine characters, and enough to
 * tell it from the other [names] (screenshots all start alike).
 */
internal fun attachmentMark(name: String, names: List<String>): String {
    val others = names.filter { it != name }
    var length = minOf(9, name.length)
    while (length < name.length && others.any { it.startsWith(name.take(length)) }) length++
    return name.take(length)
}

/**
 * The files of the mail being filled in, for Outlook's page to read a piece at a time: put whole
 * into the script, they took several times their size in memory. The page knows each file by a
 * random key, and only the latest draft's files can be read.
 */
internal class DraftFiles {
    @Volatile
    private var files: Map<String, File> = emptyMap()

    /** Makes [attachments] the files to read, in place of the ones before; returns their keys. */
    fun offer(attachments: List<MailAttachment>): List<String> {
        val keyed = attachments.associateBy({ UUID.randomUUID().toString() }, { File(it.path) })
        files = keyed
        return keyed.keys.toList()
    }

    /** The file's size, or -1 if it isn't there. */
    @JavascriptInterface
    fun size(key: String): Int = files[key]?.takeIf { it.isFile }?.length()?.toInt() ?: -1

    /** [CHUNK] bytes from [offset] on, in Base64. */
    @JavascriptInterface
    fun read(key: String, offset: Int): String {
        val file = files[key] ?: return ""
        return RandomAccessFile(file, "r").use { input ->
            input.seek(offset.toLong())
            val bytes = ByteArray(CHUNK.coerceAtMost((input.length() - offset).toInt().coerceAtLeast(0)))
            input.readFully(bytes)
            Base64.getEncoder().encodeToString(bytes)
        }
    }

    companion object {
        const val NAME = "TollyDraftFiles"
        const val CHUNK = 512 * 1024
    }
}
