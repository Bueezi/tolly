package be.tolly.core.data

import android.util.Log
import be.tolly.core.data.download.DownloadRepository
import be.tolly.core.model.ContentNode
import be.tolly.core.model.ContentType
import be.tolly.core.model.DownloadRequest
import be.tolly.core.model.DownloadStatus
import be.tolly.core.model.EmbeddedFiles
import be.tolly.core.network.di.DownloadClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** A file found while scanning courses for offline use. */
data class OfflineFile(
    val request: DownloadRequest,
    /** Bytes according to the server, or null when it didn't say. */
    val size: Long?,
    val alreadyDownloaded: Boolean,
)

/** Where a scan is: [coursesDone] of [courseCount] walked, [filesFound] files so far, sizes being checked. */
data class OfflineScanProgress(
    val courseCount: Int,
    val coursesDone: Int = 0,
    /** The courses being looked through now (they go side by side), in the order they started. */
    val scanning: List<String> = emptyList(),
    val filesFound: Int = 0,
    val sizesChecked: Int = 0,
)

/**
 * Makes courses available offline: walks every folder, learning module and page (which caches the
 * whole content tree, so it can be browsed without a connection), refreshes the grades and
 * collects every file with its size, so the user can pick what to download.
 */
@Singleton
class OfflineRepository @Inject constructor(
    private val content: ContentRepository,
    private val courses: CourseRepository,
    private val grades: GradeRepository,
    private val downloads: DownloadRepository,
    @DownloadClient private val client: OkHttpClient,
) {
    suspend fun scan(courseIds: List<String>, onProgress: (OfflineScanProgress) -> Unit): Result<List<OfflineFile>> = dataCall(inDemo = { emptyList() }) {
        val courseList = courses.observeCourses().first().associateBy { it.id }
        var progress = OfflineScanProgress(courseCount = courseIds.size)
        onProgress(progress)
        val lock = Any()
        // Reported under the lock too: walks on other threads must not overtake each other's
        // snapshots, or the screen could end on an older count than the last one.
        fun update(change: (OfflineScanProgress) -> OfflineScanProgress) {
            synchronized(lock) { onProgress(change(progress).also { progress = it }) }
        }
        // Courses, and the folders in each, load side by side; [permits] keeps it to a few requests at once.
        val permits = Semaphore(WALK_CONCURRENCY)
        val found = coroutineScope {
            courseIds.map { courseId ->
                async {
                    val courseName = courseList[courseId]?.displayName
                    val label = courseName ?: courseId
                    update { it.copy(scanning = it.scanning + label) }
                    val files = walk(courseId, courseName, parent = null, depth = 0, permits) { count ->
                        update { it.copy(filesFound = it.filesFound + count) }
                    }
                    permits.withPermit { grades.refreshCourse(courseId) } // best effort, for offline grades
                    update { it.copy(coursesDone = it.coursesDone + 1, scanning = it.scanning - label) }
                    files
                }
            }.awaitAll().flatten()
        }
        val unique = found.distinctBy { it.key }
        val existing = downloads.observeAll().first().associateBy { it.key }
        val sizePermits = Semaphore(SIZE_CONCURRENCY)
        coroutineScope {
            unique.map { request ->
                async {
                    val done = existing[request.key]?.status == DownloadStatus.DONE
                    val size = if (done) existing[request.key]?.totalBytes else sizePermits.withPermit { sizeOf(request.sourceUrl) }
                    update { it.copy(sizesChecked = it.sizesChecked + 1) }
                    OfflineFile(request, size, done)
                }
            }.awaitAll()
        }.sortedByDescending { it.size ?: -1 }
    }

    /**
     * The files under [parent], in course order. Sibling folders and files' attachments load at the
     * same time, at most [permits] requests at once; [onFound] tells how many more were found.
     */
    private suspend fun walk(
        courseId: String,
        courseName: String?,
        parent: ContentNode?,
        depth: Int,
        permits: Semaphore,
        onFound: (count: Int) -> Unit,
    ): List<DownloadRequest> {
        if (depth > MAX_DEPTH) return emptyList()
        permits.withPermit { content.refreshChildren(courseId, parent?.id) }.onFailure { error ->
            // A folder that can't be opened (hidden, or a tool) shouldn't stop the whole scan.
            if (error.toDataError() == DataError.SESSION_EXPIRED || error.toDataError() == DataError.OFFLINE) throw error
            Log.w(TAG, "Skipping a folder: ${error.toDataError()}")
            return emptyList()
        }
        val children = content.observeChildren(courseId, parent?.id).first()
        return coroutineScope {
            children.map { node ->
                async {
                    when {
                        node.type == ContentType.FILE ->
                            listOfNotNull(permits.withPermit { fileRequest(courseId, courseName, node) }).also { onFound(it.size) }
                        // Pages keep their body in an `ultraDocumentBody` child; documents in themselves.
                        node.type == ContentType.DOCUMENT ->
                            withContext(Dispatchers.Default) { EmbeddedFiles.parse(node.body) }.map { file -> content.downloadRequest(courseId, node.id, file, courseName) }
                                .also { onFound(it.size) }
                        node.type.isContainer || node.isPage -> walk(courseId, courseName, node, depth + 1, permits, onFound)
                        else -> emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun fileRequest(courseId: String, courseName: String?, node: ContentNode): DownloadRequest? {
        val attachment = content.attachments(courseId, node.id).getOrNull()?.firstOrNull() ?: return null
        return content.downloadRequest(node, attachment, courseName)
    }

    /** The file's size from a HEAD request, or from the Content-Range of a one-byte GET. */
    private suspend fun sizeOf(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            val head = runInterruptible { client.newCall(Request.Builder().url(url).head().build()).execute() }
            val length = head.use { if (it.isSuccessful) it.header("Content-Length")?.toLongOrNull() else null }
            if (length != null && length > 0) return@withContext length
            val ranged = runInterruptible {
                client.newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute()
            }
            ranged.use { it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Queues the picked files; they download in the background like any other download. */
    suspend fun download(files: List<OfflineFile>) {
        files.filterNot { it.alreadyDownloaded }.forEach { downloads.enqueue(it.request) }
    }

    private companion object {
        const val TAG = "TollyOffline"
        const val MAX_DEPTH = 12
        const val SIZE_CONCURRENCY = 4
        const val WALK_CONCURRENCY = 6
    }
}
