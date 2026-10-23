package be.tolly.core.data.download

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import be.tolly.core.auth.SessionExpiredException
import be.tolly.core.data.PreferencesRepository
import be.tolly.core.database.DownloadDao
import be.tolly.core.database.DownloadEntity
import be.tolly.core.model.DownloadStatus
import be.tolly.core.network.di.DownloadClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Downloads one file into app storage, resuming a partial `.part` file with HTTP Range. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val files: DownloadFiles,
    private val preferences: PreferencesRepository,
    @DownloadClient private val client: OkHttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_DOWNLOAD) ?: return Result.failure()
        val entity = dao.get(key) ?: return Result.failure()
        val target = files.fileFor(entity.courseId, entity.key, entity.fileName)
        val part = File(target.path + DownloadFiles.PART_SUFFIX)

        dao.updateStatus(key, DownloadStatus.RUNNING, null, null, now())
        return try {
            withContext(Dispatchers.IO) { transfer(entity, part) }
            target.delete()
            check(part.renameTo(target)) { "Could not move the finished download into place" }
            dao.updateStatus(key, DownloadStatus.DONE, target.absolutePath, null, now())
            copyToPublicDownloads(entity, target)
            Result.success()
        } catch (e: CancellationException) {
            // Cancelled by the user (row deleted) or stopped by the system (WorkManager reschedules).
            throw e
        } catch (e: SessionExpiredException) {
            fail(key, ERROR_SESSION)
        } catch (e: HttpStatusException) {
            if (e.code >= 500 && runAttemptCount < MAX_ATTEMPTS) {
                dao.updateStatus(key, DownloadStatus.QUEUED, null, null, now())
                Result.retry()
            } else {
                fail(key, "HTTP ${e.code}")
            }
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                dao.updateStatus(key, DownloadStatus.QUEUED, null, null, now())
                Result.retry()
            } else {
                fail(key, ERROR_NETWORK)
            }
        } catch (e: Exception) {
            // A bad link, a failed rename, a full disk: never leave the row RUNNING, or tapping the
            // file again would wait for a download that no longer runs.
            Log.w(TAG, "Download crashed", e)
            fail(key, e.javaClass.simpleName)
        }
    }

    private suspend fun transfer(entity: DownloadEntity, part: File) {
        part.parentFile?.mkdirs()
        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(entity.sourceUrl)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        val call = client.newCall(request)
        val response = runInterruptible { call.execute() }
        response.use {
            if (response.code == 416) {
                // Our partial file is stale; start over.
                part.delete()
                throw IOException("Range not satisfiable")
            }
            if (!response.isSuccessful) throw HttpStatusException(response.code)

            val append = response.code == 206 && existing > 0
            val bodyLength = response.body.contentLength().takeIf { it >= 0 }
            val total = bodyLength?.let { if (append) existing + it else it }
            var written = if (append) existing else 0L
            var lastReport = 0L

            FileOutputStream(part, append).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            dao.updateProgress(entity.key, written, total, DownloadStatus.RUNNING, now)
                            setProgress(workDataOf(KEY_PROGRESS to written))
                        }
                    }
                }
            }
            dao.updateProgress(entity.key, written, total ?: written, DownloadStatus.RUNNING, now())
        }
    }

    /** The copy is a convenience: the download itself succeeded even when copying fails. */
    private suspend fun copyToPublicDownloads(entity: DownloadEntity, file: File) {
        val prefs = preferences.preferences.first()
        if (!prefs.copyToDownloads || (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && prefs.downloadsFolder == null)) return
        try {
            files.exportToDownloads(entity.key, file.path, entity.fileName, entity.mimeType, entity.courseName, entity.folderPath, prefs.downloadsFolder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Copy to Downloads failed", e)
        }
    }

    private suspend fun fail(key: String, message: String): Result {
        Log.w(TAG, "Download failed: $message (attempt ${runAttemptCount + 1})")
        dao.updateStatus(key, DownloadStatus.FAILED, null, message, now())
        return Result.failure()
    }

    private fun now() = System.currentTimeMillis()

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        const val KEY_DOWNLOAD = "download_key"
        const val KEY_PROGRESS = "progress"
        const val ERROR_SESSION = "session"
        const val ERROR_NETWORK = "network"
        private const val TAG = "TollyDownloads"
        private const val MAX_ATTEMPTS = 3
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 300L
    }
}
