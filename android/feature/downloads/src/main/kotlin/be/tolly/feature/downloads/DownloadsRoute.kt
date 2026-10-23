package be.tolly.feature.downloads

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.data.download.DownloadWorker
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.fileSize
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.relativeTime
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.CourseIcon
import be.tolly.core.model.Download
import be.tolly.core.model.DownloadStatus
import be.tolly.core.ui.FileTile
import be.tolly.core.ui.HandleFileEvents
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import be.tolly.core.ui.openOfflineDownload
import be.tolly.core.ui.withHaptic
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsRoute(
    /** How the screen is left: it opens from the You tab, beside it or on top of it. */
    navigation: PaneNavigation = PaneNavigation.NONE,
    onNavigate: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Download?>(null) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val download = pendingExport
        if (uri != null && download != null) viewModel.exportTo(download, uri)
        pendingExport = null
    }

    HandleFileEvents(viewModel.events, snackbarHostState)
    LaunchedEffect(viewModel) {
        viewModel.deleted.collectLatest { download ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.downloads_deleted, download.title),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(download)
        }
    }

    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            TollyTopBar(
                large = false,
                title = { Text(stringResource(R.string.nav_downloads)) },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
                subtitle = if (state.storageUsed > 0) {
                    { Text(stringResource(R.string.downloads_storage_used, fileSize(state.storageUsed))) }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { context.openOfflineDownload() }) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = stringResource(R.string.offline_all_courses))
                    }
                    if (state.groups.isNotEmpty()) {
                        IconButton(onClick = { confirmDeleteAll = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.downloads_delete_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The list scrolls under the top bar.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> LoadingState(Modifier.padding(padding))
                state.groups.isEmpty() -> MessageState(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.downloads_empty_title),
                    body = stringResource(R.string.downloads_empty_body),
                    actionLabel = stringResource(R.string.offline_all_courses),
                    onAction = { context.openOfflineDownload() },
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = scrollingContentPadding(padding, bottom = 16.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.courseId}") {
                            GroupHeader(group)
                        }
                        itemsIndexed(group.items, key = { _, it -> it.key }) { index, download ->
                            DownloadRow(
                                download = download,
                                position = ItemPosition(index, group.items.size),
                                onOpen = { viewModel.open(download) },
                                onShare = { viewModel.share(download) },
                                onRetry = { viewModel.retry(download) },
                                onDelete = withHaptic(HapticFeedbackType.Confirm) { viewModel.delete(download) },
                                onExport = {
                                    if (state.canSaveToFolder) {
                                        viewModel.exportToDownloads(download)
                                    } else {
                                        pendingExport = download
                                        createDocument.launch(download.fileName)
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.downloads_delete_all_title)) },
            text = { Text(stringResource(R.string.downloads_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAll()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** A course's heading over its downloads: its avatar and name. */
@Composable
private fun GroupHeader(group: DownloadGroup) {
    Row(
        Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CourseAvatar(
            name = group.courseName.orEmpty(),
            seed = group.courseId,
            size = 24.dp,
            icon = group.course?.icon ?: CourseIcon.INITIALS,
            color = group.course?.color,
        )
        Text(
            group.courseName ?: stringResource(R.string.downloads_unknown_course),
            style = MaterialTheme.typography.titleSmall,
            color = if (Tolly.glass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    position: ItemPosition,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val done = download.status == DownloadStatus.DONE
    val failed = download.status == DownloadStatus.FAILED
    TollyListItem(
        modifier = modifier.groupItem(
            position,
            enabled = done || failed,
            onClick = if (done) onOpen else onRetry,
            separatorStart = GroupDefaults.separatorStart(40.dp),
        ),
        leading = { FileTile(download.mimeType, download.fileName) },
        headline = { Text(download.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = {
            Column {
                Text(statusText(download), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.QUEUED) {
                    val progress = download.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (done) {
                        MenuItem(R.string.action_open, Icons.Outlined.OpenInNew) { menuOpen = false; onOpen() }
                        MenuItem(R.string.action_save_to_downloads, Icons.Outlined.SaveAlt) { menuOpen = false; onExport() }
                        MenuItem(R.string.action_share, Icons.Outlined.Share) { menuOpen = false; onShare() }
                    }
                    if (failed) {
                        MenuItem(R.string.action_retry, Icons.Outlined.Refresh) { menuOpen = false; onRetry() }
                    }
                    MenuItem(R.string.action_delete, Icons.Outlined.Delete) { menuOpen = false; onDelete() }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun statusText(download: Download): String = when (download.status) {
    DownloadStatus.DONE -> listOfNotNull(
        download.totalBytes?.let { fileSize(it) },
        relativeTime(download.updatedAt),
    ).joinToString(" · ")
    DownloadStatus.RUNNING -> download.totalBytes?.let {
        stringResource(R.string.downloads_progress, fileSize(download.downloadedBytes), fileSize(it))
    } ?: stringResource(R.string.download_status_running)
    DownloadStatus.QUEUED -> stringResource(R.string.download_status_queued)
    DownloadStatus.FAILED -> when (download.error) {
        DownloadWorker.ERROR_SESSION -> stringResource(R.string.error_session_expired)
        DownloadWorker.ERROR_NETWORK -> stringResource(R.string.error_offline)
        else -> stringResource(R.string.download_status_failed)
    }
}
