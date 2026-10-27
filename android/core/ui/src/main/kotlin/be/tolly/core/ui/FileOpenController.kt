package be.tolly.core.ui

import android.content.Intent
import be.tolly.core.data.DataError
import be.tolly.core.data.FileActions
import be.tolly.core.data.FileTarget
import be.tolly.core.model.ContentNode
import be.tolly.core.model.Download
import be.tolly.core.model.EmbeddedFile
import be.tolly.core.model.FileViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.net.URI

/** One-off UI effects produced when the user taps a file or link. */
sealed interface FileEvent {
    data class Launch(val intent: Intent) : FileEvent

    /**
     * Open a downloaded file ([intent] views it in another app) where the user wants files to
     * open: [viewer], or null when they haven't picked yet, and their pick goes to [remember].
     */
    data class View(val intent: Intent, val viewer: FileViewer?, val remember: (FileViewer) -> Unit) : FileEvent
    data class Browse(val url: String) : FileEvent
    data object DownloadStarted : FileEvent
    data object DownloadFailed : FileEvent
    data class Error(val error: DataError) : FileEvent

    /** A copy was saved in [folder] (a label such as `Downloads/Tolly`), or as a file the user picked when null. */
    data class Saved(val folder: String?) : FileEvent
    data object SaveFailed : FileEvent
    data object Removed : FileEvent
}

/** What the long-press menu of a file can do with it. */
enum class FileAction { OPEN, OPEN_WITH, SHARE, SAVE_COPY, REMOVE }

/**
 * Owned by a ViewModel: opens downloaded files, starts downloads (and opens them when they finish
 * while the screen is still around) and routes other links to the browser.
 */
class FileOpenController(
    private val scope: CoroutineScope,
    private val actions: FileActions,
) {
    private val channel = Channel<FileEvent>(Channel.BUFFERED)
    val events: Flow<FileEvent> = channel.receiveAsFlow()

    fun open(node: ContentNode) {
        scope.launch { handle(actions.openOrDownload(node)) }
    }

    fun open(courseId: String, ownerId: String?, file: EmbeddedFile) {
        scope.launch { handle(actions.openOrDownload(courseId, ownerId, file)) }
    }

    /** Does [action] with [target], downloading it first when it isn't on the device yet. */
    fun act(target: FileTarget, action: FileAction) {
        scope.launch {
            if (action == FileAction.REMOVE) {
                actions.delete(target)
                channel.send(FileEvent.Removed)
                return@launch
            }
            if (actions.downloaded(target) == null) channel.send(FileEvent.DownloadStarted)
            val download = actions.fetch(target)
            if (download == null) {
                channel.send(FileEvent.DownloadFailed)
                return@launch
            }
            perform(download, action)
        }
    }

    /** Does [action] with a file that is already on the device (removing is the caller's own). */
    fun act(download: Download, action: FileAction) {
        scope.launch { perform(download, action) }
    }

    private suspend fun perform(download: Download, action: FileAction) {
        val event = when (action) {
            FileAction.OPEN -> actions.viewIntent(download)?.let { view(it) }
            FileAction.OPEN_WITH -> actions.viewIntent(download)?.let { view ->
                FileEvent.Launch(Intent.createChooser(view, download.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            FileAction.SHARE -> actions.shareIntent(download)?.let(FileEvent::Launch)
            FileAction.SAVE_COPY -> actions.saveCopy(download)?.let(FileEvent::Saved) ?: FileEvent.SaveFailed
            FileAction.REMOVE -> null
        }
        channel.send(event ?: FileEvent.DownloadFailed)
    }

    fun browse(url: String) {
        channel.trySend(FileEvent.Browse(url))
    }

    /**
     * Handles a link tapped inside rendered HTML: embedded Toledo files are downloaded, everything
     * else opens in the browser.
     */
    fun onLink(url: String, courseId: String, ownerId: String?, embedded: List<EmbeddedFile>) {
        val match = embedded.firstOrNull { it.url.substringBefore('?') == url.substringBefore('?') }
        when {
            match != null -> open(courseId, ownerId, match)
            url.contains("/bbcswebdav/") -> {
                val name = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull().orEmpty().ifBlank { "file" }
                open(courseId, ownerId, EmbeddedFile(displayName = name, mimeType = null, url = url))
            }
            else -> browse(url)
        }
    }

    private suspend fun view(intent: Intent): FileEvent =
        FileEvent.View(intent, actions.fileViewer()) { viewer -> scope.launch { actions.setFileViewer(viewer) } }

    private suspend fun handle(outcome: FileActions.Outcome) {
        when (outcome) {
            is FileActions.Outcome.Open -> channel.send(view(outcome.intent))
            is FileActions.Outcome.Failed -> channel.send(FileEvent.Error(outcome.error))
            is FileActions.Outcome.Downloading -> {
                channel.send(FileEvent.DownloadStarted)
                val intent = actions.awaitOpenIntent(outcome.key)
                channel.send(if (intent != null) view(intent) else FileEvent.DownloadFailed)
            }
        }
    }
}
