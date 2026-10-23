package be.tolly.core.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.fileIcon
import be.tolly.core.designsystem.component.fileSize
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadStatus

/** A file's type icon on a tonal tile, leading a row for that file. */
@Composable
fun FileTile(mimeType: String?, name: String?, size: Dp = 40.dp) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(fileIcon(mimeType, name), contentDescription = null) }
    }
}

/**
 * Copies of downloads can go straight into a folder: [downloadsFolder] when the user picked one,
 * else Downloads/Tolly, which can only be written to from Android 10.
 */
fun canSaveCopies(downloadsFolder: String?): Boolean =
    downloadsFolder != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** "PDF" for `notes.pdf`: a file's extension as its type, when it is a short one. */
fun fileTypeLabel(name: String?): String? =
    name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }?.uppercase()

/**
 * A download's state for a file row's supporting line: its size once it is on the device, or
 * that it is on its way or failed. Null while the file was never downloaded.
 */
@Composable
fun downloadStatusText(download: Download?): String? = when (download?.status) {
    DownloadStatus.DONE -> download.totalBytes?.let { fileSize(it) } ?: stringResource(R.string.download_status_done)
    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> stringResource(R.string.download_status_running)
    DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    null -> null
}

/**
 * Whether a file is on the device, on its way (with its progress when known), or failed. While
 * it is none of those, a download arrow when [showDownload], else nothing.
 */
@Composable
fun DownloadStatusIcon(
    download: Download?,
    modifier: Modifier = Modifier,
    showDownload: Boolean = true,
    strokeWidth: Dp = 3.dp,
) {
    when (download?.status) {
        DownloadStatus.DONE -> Icon(
            Icons.Outlined.DownloadDone,
            contentDescription = stringResource(R.string.download_status_done),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
            val progress = download.progress
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = modifier, strokeWidth = strokeWidth)
            } else {
                CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
            }
        }
        DownloadStatus.FAILED -> Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(R.string.download_status_failed),
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        null -> if (showDownload) {
            Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.action_download), modifier = modifier)
        }
    }
}
