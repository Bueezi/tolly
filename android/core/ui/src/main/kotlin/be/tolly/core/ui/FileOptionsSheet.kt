package be.tolly.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.IconTileSize
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.fileSize
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadStatus

/**
 * What a long press on a file offers: open it, open it with an app of choice, share it, save a
 * copy, or remove it from the device. Files that aren't downloaded yet are downloaded first.
 * [onOpenInToledo] adds a row for the file's page in Toledo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOptionsSheet(
    name: String,
    mimeType: String?,
    download: Download?,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit,
    onOpenInToledo: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val onDevice = download?.status == DownloadStatus.DONE
    val rows = buildList {
        add(Option(Icons.Outlined.FileOpen, TileColor.Blue, R.string.action_open) { onAction(FileAction.OPEN) })
        add(Option(Icons.Outlined.Apps, TileColor.Indigo, R.string.action_open_with) { onAction(FileAction.OPEN_WITH) })
        add(Option(Icons.Outlined.Share, TileColor.Green, R.string.action_share) { onAction(FileAction.SHARE) })
        add(Option(Icons.Outlined.SaveAlt, TileColor.Teal, R.string.action_save_to_downloads) { onAction(FileAction.SAVE_COPY) })
        if (onOpenInToledo != null) add(Option(Icons.Outlined.Public, TileColor.Gray, R.string.action_open_in_toledo, onOpenInToledo))
        if (onDevice) {
            add(
                Option(Icons.Outlined.Delete, TileColor.Red, R.string.file_remove) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAction(FileAction.REMOVE)
                },
            )
        }
    }
    TollySheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            TollyListItem(
                leading = { FileTile(mimeType, name, size = 44.dp) },
                headline = { Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supporting = {
                    Text(
                        when (download?.status) {
                            DownloadStatus.DONE -> download.totalBytes?.let { stringResource(R.string.file_on_device_size, fileSize(it)) }
                                ?: stringResource(R.string.download_status_done)
                            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> stringResource(R.string.download_status_running)
                            else -> stringResource(R.string.file_not_downloaded)
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            rows.forEachIndexed { index, option ->
                TollyListItem(
                    modifier = Modifier.groupItem(
                        ItemPosition(index, rows.size),
                        onClick = {
                            onDismiss()
                            option.onClick()
                        },
                        separatorStart = GroupDefaults.separatorStart(IconTileSize),
                    ),
                    leading = { IconTile(option.icon, option.color) },
                    headline = {
                        Text(
                            stringResource(option.label),
                            color = if (option.color == TileColor.Red) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailing = if (option.label == R.string.action_open_in_toledo) {
                        { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private class Option(val icon: ImageVector, val color: TileColor, val label: Int, val onClick: () -> Unit)
