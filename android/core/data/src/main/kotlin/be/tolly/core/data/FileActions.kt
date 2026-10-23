package be.tolly.core.data

import android.content.Intent
import be.tolly.core.data.download.DownloadRepository
import be.tolly.core.model.ContentNode
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadRequest
import be.tolly.core.model.DownloadStatus
import be.tolly.core.model.EmbeddedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A file the user can act on: a content item, or a file embedded in a document or announcement body. */
sealed interface FileTarget {
    val courseId: String
    val name: String
    val mimeType: String?

    /** The key of its download, see [DownloadRequest.key]. */
    val key: String

    data class Item(val node: ContentNode) : FileTarget {
        override val courseId: String get() = node.courseId
        override val name: String get() = node.downloadFileName
        override val mimeType: String? get() = node.mimeType
        override val key: String get() = FileActions.keyFor(node)
    }

    data class Embedded(override val courseId: String, val ownerId: String?, val file: EmbeddedFile) : FileTarget {
        override val name: String get() = file.displayName
        override val mimeType: String? get() = file.mimeType
        override val key: String get() = FileActions.keyFor(courseId, ownerId, file.displayName)
    }
}

/** "Tap a file" logic shared by the content browser, documents and announcements. */
@Singleton
class FileActions @Inject constructor(
    private val downloads: DownloadRepository,
    private val content: ContentRepository,
    private val courses: CourseRepository,
    private val preferences: PreferencesRepository,
) {
    sealed interface Outcome {
        /** The file is on the device; launch this intent. */
        data class Open(val intent: Intent) : Outcome

        /** A download started (or was already running) under [key]. */
        data class Downloading(val key: String) : Outcome
        data class Failed(val error: DataError) : Outcome
    }

    /** Opens a downloaded `resource/x-bb-file` item, or starts downloading it. */
    suspend fun openOrDownload(node: ContentNode): Outcome {
        val existing = downloads.get(keyFor(node))
        existing?.let(::openIntent)?.let { return Outcome.Open(it) }

        val attachment = content.attachments(node.courseId, node.id)
            .getOrElse { return Outcome.Failed(it.toDataError()) }
            .firstOrNull() ?: return Outcome.Failed(DataError.NOT_AVAILABLE)
        val request = content.downloadRequest(node, attachment, courseName(node.courseId))
        return Outcome.Downloading(downloads.enqueue(request))
    }

    /** Same for a file embedded in a document or announcement body. */
    suspend fun openOrDownload(courseId: String, ownerId: String?, file: EmbeddedFile): Outcome {
        val existing = downloads.get(keyFor(courseId, ownerId, file.displayName))
        existing?.let(::openIntent)?.let { return Outcome.Open(it) }
        val request = content.downloadRequest(courseId, ownerId, file, courseName(courseId))
        return Outcome.Downloading(downloads.enqueue(request))
    }

    /** Suspends until the download under [key] finished; returns the intent to open it, or null on failure. */
    suspend fun awaitOpenIntent(key: String): Intent? = awaitFinished(key)?.let(::openIntent)

    /** Waits for the download under [key] to finish or fail; null if it was removed meanwhile. */
    private suspend fun awaitFinished(key: String): Download? =
        downloads.observe(key)
            .filter { it == null || it.status == DownloadStatus.DONE || it.status == DownloadStatus.FAILED }
            .first()

    fun observeCourseDownloads(courseId: String): Flow<Map<String, Download>> = downloads.observeForCourse(courseId)

    /** The finished download of [target], or null while it isn't on the device. */
    suspend fun downloaded(target: FileTarget): Download? =
        downloads.get(target.key)?.takeIf { downloads.viewIntent(it) != null }

    /** [target] once it's on the device, downloading it first if needed; null if that failed. */
    suspend fun fetch(target: FileTarget): Download? {
        downloaded(target)?.let { return it }
        val outcome = when (target) {
            is FileTarget.Item -> openOrDownload(target.node)
            is FileTarget.Embedded -> openOrDownload(target.courseId, target.ownerId, target.file)
        }
        return when (outcome) {
            is Outcome.Open -> downloaded(target)
            is Outcome.Downloading -> awaitFinished(outcome.key)?.takeIf { it.status == DownloadStatus.DONE }
            is Outcome.Failed -> null
        }
    }

    fun viewIntent(download: Download): Intent? = downloads.viewIntent(download)

    fun shareIntent(download: Download): Intent? = downloads.shareIntent(download)

    /** Copies [download] to the folder for copies; returns that folder's label, or null if it failed. */
    suspend fun saveCopy(download: Download): String? = runCatching { downloads.exportToDownloads(download) }
        .map { downloads.folderLabel(preferences.preferences.first().downloadsFolder) }
        .getOrNull()

    suspend fun delete(target: FileTarget) = downloads.delete(target.key)

    private fun openIntent(download: Download): Intent? =
        if (download.status == DownloadStatus.DONE) downloads.viewIntent(download) else null

    private suspend fun courseName(courseId: String): String? =
        courses.observeCourse(courseId).map { it?.displayName }.firstOrNull()

    companion object {
        fun keyFor(courseId: String, contentId: String?, fileName: String): String = DownloadRequest.key(courseId, contentId, fileName)

        fun keyFor(node: ContentNode): String = DownloadRequest.key(node.courseId, node.id, node.downloadFileName)
    }
}

/** The name a content item's file is saved under, which is also part of its download key. */
internal val ContentNode.downloadFileName: String get() = fileName ?: title
