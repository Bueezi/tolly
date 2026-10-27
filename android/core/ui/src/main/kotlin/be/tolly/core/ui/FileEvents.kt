package be.tolly.core.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.data.DataError
import be.tolly.core.designsystem.R
import be.tolly.core.model.FileViewer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * Collects [events] while composed: launches viewers, opens links and shows snackbars. The first
 * file Tolly can show itself asks where files should open ([ViewerChoiceSheet]).
 */
@Composable
fun HandleFileEvents(events: Flow<FileEvent>, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A file waiting for the user to say where files open.
    var choosing by remember { mutableStateOf<FileEvent.View?>(null) }
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
    LaunchedEffect(events) {
        coroutineScope {
            events.collect { event ->
                val message: String? = when (event) {
                    is FileEvent.Launch -> if (context.tryStart(event.intent)) null else context.getString(R.string.error_no_app)
                    is FileEvent.View -> when {
                        !viewsInApp(event.intent) -> if (context.tryStart(event.intent)) null else context.getString(R.string.error_no_app)
                        event.viewer == null -> {
                            choosing = event
                            null
                        }
                        else -> if (context.view(event.intent, event.viewer)) null else context.getString(R.string.error_no_app)
                    }
                    is FileEvent.Browse -> {
                        context.openInBrowser(event.url)
                        null
                    }
                    FileEvent.DownloadStarted -> context.getString(R.string.download_started)
                    FileEvent.DownloadFailed -> context.getString(R.string.download_failed)
                    is FileEvent.Error -> context.getString(dataErrorMessage(event.error))
                    is FileEvent.Saved -> event.folder?.let { context.getString(R.string.downloads_exported_to, it) }
                        ?: context.getString(R.string.downloads_exported)
                    FileEvent.SaveFailed -> context.getString(R.string.downloads_export_failed)
                    FileEvent.Removed -> context.getString(R.string.file_removed)
                }
                if (message != null) showMessage(message)
            }
        }
    }
    choosing?.let { pending ->
        ViewerChoiceSheet(
            onPick = { viewer ->
                choosing = null
                pending.remember(viewer)
                if (!context.view(pending.intent, viewer)) showMessage(context.getString(R.string.error_no_app))
            },
            onDismiss = { choosing = null },
        )
    }
}

/** Opens the file of [intent] in Tolly's own viewer or in another app, as [viewer] says. */
private fun Context.view(intent: Intent, viewer: FileViewer): Boolean =
    tryStart(if (viewer == FileViewer.IN_APP) inAppViewer(intent) else intent)

@StringRes
fun dataErrorMessage(error: DataError): Int = when (error) {
    DataError.SESSION_EXPIRED -> R.string.error_session_expired
    DataError.OFFLINE -> R.string.error_offline
    DataError.NOT_AVAILABLE -> R.string.error_not_available
    DataError.SERVER -> R.string.error_server
}

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Intent action of the app's signed-in browser for Toledo pages (declared by the app module). */
const val ACTION_OPEN_TOLEDO = "be.tolly.action.OPEN_TOLEDO"

/**
 * Opens [url]: Toledo pages in the app's own browser, which shares the session so the user stays
 * signed in; everything else in a Custom Tab.
 */
fun Context.openInBrowser(url: String) {
    if (UltraEndpoints.opensSignedIn(url)) {
        val intent = Intent(ACTION_OPEN_TOLEDO, Uri.parse(url)).setPackage(packageName)
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(intent)) return
    }
    openExternally(url)
}

/**
 * Opens [url] in a Custom Tab, falling back to any browser; mail and phone links go to their
 * apps. Links come from teachers' HTML, so other schemes open nothing: handing a `file:` URI to
 * another app throws, and `content:` or `intent:` would reach into this app or others.
 */
fun Context.openExternally(url: String) {
    val uri = Uri.parse(url)
    when (uri.scheme?.lowercase()) {
        "http", "https" -> try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            tryStart(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        "mailto", "tel" -> tryStart(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Intent action of the app's video player (declared by the app module). */
const val ACTION_PLAY_VIDEO = "be.tolly.action.PLAY_VIDEO"
const val EXTRA_VIDEO_TITLE = "title"
const val EXTRA_VIDEO_WEB_URL = "webUrl"
const val EXTRA_VIDEO_LIVE = "live"
const val EXTRA_VIDEO_KEY = "key"

/**
 * Plays the HLS stream [url] in the app's player. [webUrl] is the video's page, for the player's
 * "open in browser"; a [live] stream starts at the live edge. A recording with a [key] (its entry
 * id) continues where the user left it, and keeps how far they got.
 */
fun Context.playVideo(url: String, title: String, webUrl: String?, live: Boolean = false, key: String? = null) {
    val intent = Intent(ACTION_PLAY_VIDEO, Uri.parse(url)).setPackage(packageName)
        .putExtra(EXTRA_VIDEO_TITLE, title)
        .putExtra(EXTRA_VIDEO_WEB_URL, webUrl)
        .putExtra(EXTRA_VIDEO_LIVE, live)
        .putExtra(EXTRA_VIDEO_KEY, key)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    tryStart(intent)
}

/** Intent action of the app's "download for offline" screen (declared by the app module). */
const val ACTION_DOWNLOAD_OFFLINE = "be.tolly.action.DOWNLOAD_OFFLINE"

/** Opens the "download for offline" screen for [courseIds], or for all of this year's courses when empty. */
fun Context.openOfflineDownload(courseIds: List<String> = emptyList()) {
    val intent = Intent(ACTION_DOWNLOAD_OFFLINE).setPackage(packageName).putExtra("courseIds", courseIds.toTypedArray())
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    tryStart(intent)
}
