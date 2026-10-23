package be.tolly.core.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.data.toModel
import be.tolly.core.database.DownloadDao
import be.tolly.core.database.DownloadEntity
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadRequest
import be.tolly.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val files: DownloadFiles,
    private val preferences: PreferencesRepository,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun observeAll(): Flow<List<Download>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(key: String): Flow<Download?> = dao.observe(key).map { it?.toModel() }

    /** Downloads of one course keyed by [Download.key]. */
    fun observeForCourse(courseId: String): Flow<Map<String, Download>> =
        dao.observeForCourse(courseId).map { list -> list.associate { it.key to it.toModel() } }

    /** Queues [request] unless the file is already downloaded or in progress. Returns its key. */
    suspend fun enqueue(request: DownloadRequest): String {
        val key = request.key
        val existing = dao.get(key)
        val alreadyThere = existing?.status == DownloadStatus.DONE && existing.localPath?.let { File(it).exists() } == true
        val inProgress = existing?.status == DownloadStatus.RUNNING || existing?.status == DownloadStatus.QUEUED
        if (alreadyThere || inProgress) return key

        dao.upsert(
            DownloadEntity(
                key = key,
                courseId = request.courseId,
                courseName = request.courseName,
                contentId = request.contentId,
                title = request.title,
                fileName = request.fileName,
                mimeType = request.mimeType,
                sourceUrl = request.sourceUrl,
                totalBytes = null,
                downloadedBytes = 0,
                status = DownloadStatus.QUEUED,
                localPath = null,
                error = null,
                updatedAt = System.currentTimeMillis(),
                folderPath = request.folderPath,
            ),
        )
        startWork(key)
        return key
    }

    suspend fun retry(key: String) {
        val existing = dao.get(key) ?: return
        dao.upsert(existing.copy(status = DownloadStatus.QUEUED, error = null, updatedAt = System.currentTimeMillis()))
        startWork(key)
    }

    suspend fun get(key: String): Download? = dao.get(key)?.toModel()

    suspend fun delete(key: String) {
        workManager.cancelUniqueWork(workName(key))
        val entity = dao.get(key)
        dao.delete(key)
        withContext(Dispatchers.IO) {
            if (entity != null) {
                files.delete(entity.localPath ?: files.fileFor(entity.courseId, entity.key, entity.fileName).path)
            }
        }
    }

    suspend fun deleteAll() {
        workManager.cancelAllWorkByTag(WORK_TAG)
        dao.deleteAll()
        withContext(Dispatchers.IO) { files.deleteAll() }
    }

    suspend fun storageUsed(): Long = withContext(Dispatchers.IO) { files.totalSize() }

    fun viewIntent(download: Download): Intent? =
        download.localPath?.takeIf { File(it).exists() }?.let { files.viewIntent(it, download.mimeType) }

    fun shareIntent(download: Download): Intent? =
        download.localPath?.takeIf { File(it).exists() }?.let { files.shareIntent(it, download.mimeType) }

    /** Copies a finished download to the folder the user chose for copies, or Downloads/Tolly. */
    suspend fun exportToDownloads(download: Download): Uri {
        val path = requireNotNull(download.localPath) { "Not downloaded" }
        val folder = preferences.preferences.first().downloadsFolder
        return files.exportToDownloads(download.key, path, download.fileName, download.mimeType, download.courseName, download.folderPath, folder)
    }

    /** Where copies go, for showing: the picked folder or `Downloads/Tolly`. */
    fun folderLabel(folder: String?): String = files.folderLabel(folder)

    /**
     * Sends copies to the folder at [treeUri] from now on (null: back to Downloads/Tolly). The app
     * keeps access to the new folder across restarts and gives up the old one.
     */
    suspend fun setFolder(treeUri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val old = preferences.preferences.first().downloadsFolder
        if (treeUri != null) resolver.takePersistableUriPermission(treeUri, flags)
        preferences.setDownloadsFolder(treeUri?.toString())
        if (old != null && old != treeUri?.toString()) runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
    }

    /** Copies every finished download to the public Downloads folder; returns how many failed. */
    suspend fun exportAllToDownloads(): Int {
        var failed = 0
        for (download in dao.getAll().map { it.toModel() }) {
            if (download.status != DownloadStatus.DONE || download.localPath?.let { File(it).exists() } != true) continue
            try {
                exportToDownloads(download)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "Copy to Downloads failed for a ${download.mimeType} file", e)
            }
        }
        return failed
    }

    suspend fun copyTo(download: Download, target: Uri) {
        files.copyTo(requireNotNull(download.localPath) { "Not downloaded" }, target)
    }

    private fun startWork(key: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD to key))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(key), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(key: String) = "download:$key"

    private companion object {
        const val WORK_TAG = "download"
        const val TAG = "TollyDownloads"
    }
}
