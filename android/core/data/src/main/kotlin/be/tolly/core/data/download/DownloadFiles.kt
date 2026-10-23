package be.tolly.core.data.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Where downloaded files live (app-private storage) and how they are opened or exported. */
@Singleton
class DownloadFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File get() = File(context.filesDir, "downloads")

    fun fileFor(courseId: String, key: String, fileName: String): File {
        val folder = File(File(root, safeName(courseId)), shortHash(key))
        return File(folder, safeName(fileName).ifBlank { "file" })
    }

    fun totalSize(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun deleteAll() {
        root.deleteRecursively()
    }

    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        file.delete()
        File(file.path + PART_SUFFIX).delete()
        file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
    }

    /** An `ACTION_VIEW` intent for a downloaded file, shared through the [FileProvider]. */
    fun viewIntent(path: String, mimeType: String?): Intent {
        val file = File(path)
        val uri = context.fileProviderUri(file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, resolveMimeType(file.name, mimeType))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(path: String, mimeType: String?): Intent {
        val file = File(path)
        val uri = context.fileProviderUri(file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(resolveMimeType(file.name, mimeType))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Copies a downloaded file to `<course>/<folders>` inside [folder] (a folder the user picked,
     * as a tree URI) or, without one, inside the public `Download/Tolly` folder (Android 10+).
     * The copy [owner] (the download's key) made there earlier is overwritten; another file that
     * happens to have the same name (two "Slides.pdf" in one folder) gets a name of its own.
     * Returns the copy's URI.
     */
    suspend fun exportToDownloads(
        owner: String,
        path: String,
        fileName: String,
        mimeType: String?,
        courseName: String?,
        folderPath: String = "",
        folder: String? = null,
    ): Uri =
        withContext(Dispatchers.IO) {
            val folders = listOfNotNull(courseName?.let(::safeName)?.takeIf { it.isNotBlank() }) +
                folderPath.split('/').map(::safeName).filter { it.isNotBlank() && it != "." && it != ".." }
            if (folder != null) return@withContext exportToTree(owner, Uri.parse(folder), folders, path, fileName)
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "MediaStore downloads need Android 10" }
            val resolver = context.contentResolver
            val subFolder = (listOf(DEFAULT_FOLDER) + folders).joinToString(File.separator)
            val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + subFolder + File.separator
            val place = "$owner|$relativePath"
            val earlier = copies.getString(place, null)?.let(Uri::parse)?.takeIf(::exists)
                ?: findOwnDownload(relativePath, safeName(fileName))?.takeIf { !isClaimed(it) }
            earlier?.let { existing ->
                copyFile(path, existing, "wt")
                remember(place, existing)
                return@withContext existing
            }
            val displayName = freeName(safeName(fileName)) { findOwnDownload(relativePath, it) != null }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, resolveMimeType(fileName, mimeType))
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Could not create download entry"
            }
            try {
                copyFile(path, uri)
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            remember(place, uri)
            uri
        }

    /** Copies the file into [folders] below the picked [tree], creating what is missing. */
    private fun exportToTree(owner: String, tree: Uri, folders: List<String>, path: String, fileName: String): Uri {
        val resolver = context.contentResolver
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for (name in folders) {
            parent = findChild(tree, parent, name)
                ?: requireNotNull(DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, name)) { "Could not create a folder" }
        }
        val name = safeName(fileName).ifBlank { "file" }
        val place = "$owner|$parent"
        val target = copies.getString(place, null)?.let(Uri::parse)?.takeIf(::exists)
            ?: findChild(tree, parent, name)?.takeIf { !isClaimed(it) }
            ?: run {
                val displayName = freeName(name) { findChild(tree, parent, it) != null }
                // The type from the name, so the provider doesn't add an extension of its own.
                val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"
                requireNotNull(DocumentsContract.createDocument(resolver, parent, type, displayName)) { "Could not create the file" }
            }
        copyFile(path, target, "wt")
        remember(place, target)
        return target
    }

    /** Which copy each download made where: `<download key>|<folder>` to the copy's URI. */
    private val copies by lazy { context.getSharedPreferences("download_copies", Context.MODE_PRIVATE) }

    private fun remember(place: String, uri: Uri) = copies.edit().putString(place, uri.toString()).apply()

    /** Whether another download's copy is this file. */
    private fun isClaimed(uri: Uri): Boolean = uri.toString() in copies.all.values

    private fun exists(uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() }
    }.getOrNull() == true

    private fun findChild(tree: Uri, parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        context.contentResolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        return null
    }

    /**
     * How to show where copies go: the picked folder's path on its storage (`Documents/Uni`), its
     * name for other providers, or `Downloads/Tolly` without one.
     */
    fun folderLabel(folder: String?): String {
        val tree = folder?.let(Uri::parse) ?: return "Downloads/$DEFAULT_FOLDER"
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        val path = documentId.substringAfter(':', "")
        if (documentId.contains(':') && path.isNotBlank()) return path
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        return runCatching {
            context.contentResolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: documentId
    }

    /** A file this app created earlier in [relativePath]; other apps' files aren't visible without a permission. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findOwnDownload(relativePath: String, displayName: String): Uri? = runCatching {
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(relativePath, displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)) else null
        }
    }.getOrNull()

    /** Copies a downloaded file to a document the user picked (Storage Access Framework). */
    suspend fun copyTo(path: String, target: Uri) = withContext(Dispatchers.IO) { copyFile(path, target) }

    /** Writes the file at [path] to the content URI [target] ("w", or "wt" to truncate what was there). */
    private fun copyFile(path: String, target: Uri, mode: String = "w") {
        context.contentResolver.openOutputStream(target, mode).use { out ->
            File(path).inputStream().use { input -> input.copyTo(requireNotNull(out)) }
        }
    }

    companion object {
        const val PART_SUFFIX = ".part"
        private const val DEFAULT_FOLDER = "Tolly"
        private const val MAX_NAME = 120

        fun resolveMimeType(fileName: String, mimeType: String?): String {
            val fromName = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            return when {
                mimeType.isNullOrBlank() || mimeType == "application/octet-stream" -> fromName ?: "application/octet-stream"
                else -> mimeType
            }
        }

        internal fun safeName(name: String): String {
            val clean = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim()
            if (clean.length <= MAX_NAME) return clean
            // Shorten the name, not the extension, or the copy no longer opens as what it is.
            val extension = clean.substringAfterLast('.', "").takeIf { it.length in 1..10 }
                ?: return clean.take(MAX_NAME)
            return clean.dropLast(extension.length + 1).take(MAX_NAME - extension.length - 1).trimEnd() + "." + extension
        }

        /** [name] if not [taken], else "name (2).ext", "name (3).ext"…. */
        internal fun freeName(name: String, taken: (String) -> Boolean): String {
            if (!taken(name)) return name
            val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && '.' in name }
            val base = if (extension != null) name.dropLast(extension.length + 1) else name
            return generateSequence(2) { it + 1 }
                .map { n -> "$base ($n)" + (extension?.let { ".$it" } ?: "") }
                .first { !taken(it) }
        }

        private fun shortHash(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .take(6).joinToString("") { "%02x".format(it) }
    }
}

/** A `content://` URI for one of the app's own files, from the FileProvider in this module's manifest. */
internal fun Context.fileProviderUri(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
