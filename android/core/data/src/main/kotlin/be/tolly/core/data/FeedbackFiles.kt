package be.tolly.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.download.fileProviderUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A file for a mail written in Outlook on the web: its name and type, and where it is on disk. */
class MailAttachment(val name: String, val mimeType: String, val path: String)

/**
 * Files the user adds to their feedback, screenshots mostly. Each is copied into the cache as it
 * is picked: the picker's permission to read it ends with the app, the feedback draft doesn't.
 * A big picture is made smaller; together the files stay under [MAX_TOTAL_BYTES], about what a
 * mail takes.
 */
@Singleton
class FeedbackFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.cacheDir, "feedback")

    // Adding and tidying up take turns: the size check counts what was added before, and tidying
    // up mustn't take a copy that is still being made.
    private val turns = Mutex()

    /** Why a picked file didn't come; [TOO_BIG] wins when there are several. */
    enum class Problem { TOO_BIG, FAILED }

    /**
     * Copies the picked [uris] in, after the draft's [current] files (read once it is this call's
     * turn), handing each copy's path to [onAdded].
     */
    suspend fun add(uris: List<Uri>, current: () -> List<String>, onAdded: (String) -> Unit): Problem? = turns.withLock {
        val paths = current()
        var used = withContext(Dispatchers.IO) { paths.sumOf { File(it).length() } }
        var problem: Problem? = null
        for (uri in uris) {
            val copy = copy(uri)
            val size = copy?.let { withContext(Dispatchers.IO) { it.length() } }
            when {
                copy == null || size == null -> if (problem == null) problem = Problem.FAILED
                used + size > MAX_TOTAL_BYTES -> {
                    remove(copy.path)
                    problem = Problem.TOO_BIG
                }
                else -> {
                    used += size
                    onAdded(copy.path)
                }
            }
        }
        problem
    }

    /**
     * Throws away the copies that [keep] (read once it is this call's turn) doesn't list: those of
     * a draft that was lost, when the app was closed from the recent apps.
     */
    suspend fun tidy(keep: () -> List<String>) = turns.withLock {
        val kept = keep().mapNotNull { File(it).parentFile }.toSet()
        withContext(Dispatchers.IO) { dir.listFiles()?.filter { it !in kept }?.forEach { it.deleteRecursively() } }
    }

    /** Takes out a file that [add] made. */
    suspend fun remove(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile?.parentFile == dir) file.parentFile?.deleteRecursively()
    }

    /** A uri for a mail app to read the file through. */
    internal fun uri(path: String): Uri = context.fileProviderUri(File(path))

    /** The files that are still there, for Outlook's form. */
    internal fun attachments(paths: List<String>): List<MailAttachment> =
        paths.map(::File).filter { it.isFile }.map { MailAttachment(it.name, mimeType(it), it.path) }

    /** A copy of [uri] in a folder of its own, which keeps its name even when two have the same one; null if it can't be read. */
    private suspend fun copy(uri: Uri): File? = withContext(Dispatchers.IO) {
        val folder = File(dir, System.nanoTime().toString())
        try {
            val name = displayName(uri)?.replace(Regex("""[/\\:*?"<>|]"""), "_")?.takeIf { it.isNotBlank() } ?: "file"
            folder.mkdirs()
            val file = File(folder, name)
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> file.outputStream().use { input.copyTo(it) } }
            if (file.length() > MAX_IMAGE_BYTES && mimeType(file).startsWith("image/")) shrink(file) ?: file else file
        } catch (e: CancellationException) {
            folder.deleteRecursively()
            throw e
        } catch (_: Exception) {
            folder.deleteRecursively()
            null
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    /**
     * The picture at most [MAX_IMAGE_SIDE] on its longest side, as a JPEG in place of [file]; null
     * if it can't be made, leaving [file] as it was. It is written beside it first, so a failed
     * write never leaves half a picture.
     */
    private fun shrink(file: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val temp = File(file.parentFile, ".shrinking")
        val written = runCatching { temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } }.getOrDefault(false)
        val jpeg = File(file.parentFile, file.nameWithoutExtension + ".jpg")
        if (!written || !temp.renameTo(jpeg)) {
            temp.delete()
            return null
        }
        if (jpeg != file) file.delete()
        return jpeg
    }

    companion object {
        const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 2L * 1024 * 1024
        private const val MAX_IMAGE_SIDE = 2560
        private const val JPEG_QUALITY = 88
    }
}
