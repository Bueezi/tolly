package be.tolly.feature.downloads.offline

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.data.OfflineFile
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.TollyBackButton
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySwitch
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.fileIcon
import be.tolly.core.designsystem.component.fileSize
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.groupShape
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.withHaptic

/** Pick which files of one or more courses to download for offline use, biggest first. */
@Composable
fun OfflineScreen(onClose: () -> Unit, viewModel: OfflineViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OfflineContent(
        state = state,
        actions = OfflineActions(
            onClose = onClose,
            onRetry = viewModel::scan,
            onToggle = viewModel::toggle,
            onSelectAll = viewModel::selectAll,
            onSelectNone = viewModel::selectNone,
            onHideDownloaded = viewModel::setHideDownloaded,
            onCopyToDownloads = viewModel::setCopyToDownloads,
            onDownload = viewModel::download,
        ),
    )
}

/** What the offline screen can do; split from the view model so the screen can be shown with any state. */
class OfflineActions(
    val onClose: () -> Unit,
    val onRetry: () -> Unit,
    val onToggle: (OfflineFile) -> Unit,
    val onSelectAll: () -> Unit,
    val onSelectNone: () -> Unit,
    val onHideDownloaded: (Boolean) -> Unit,
    val onCopyToDownloads: (Boolean) -> Unit,
    val onDownload: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineContent(state: OfflineUiState, actions: OfflineActions) {
    val context = LocalContext.current
    val phase = state.phase
    val onClose = actions.onClose

    if (phase is OfflinePhase.Queued) {
        val message = pluralStringResource(R.plurals.offline_queued, phase.count, phase.count)
        LaunchedEffect(phase) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onClose()
        }
    }

    TollyScaffold(
        topBar = {
            TollyTopBar(
                detail = true,
                title = { Text(stringResource(R.string.offline_title)) },
                subtitle = {
                    Text(
                        state.courseName ?: pluralStringResource(R.plurals.offline_courses, state.courseCount, state.courseCount),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TollyBackButton(onClick = onClose, close = true) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                when (phase) {
                    is OfflinePhase.Scanning -> ScanningState(phase)
                    is OfflinePhase.Failed -> ListMessageState(
                        icon = Icons.Outlined.CloudDownload,
                        emptyTitle = "",
                        error = phase.error,
                        onRetry = actions.onRetry,
                    )
                    is OfflinePhase.Ready, is OfflinePhase.Queued -> if (state.files.isEmpty()) {
                        MessageState(icon = Icons.Outlined.CloudDownload, title = stringResource(R.string.offline_empty))
                    } else {
                        FileList(state, actions)
                    }
                }
            }
            if (phase is OfflinePhase.Ready && phase.files.isNotEmpty()) SummaryBar(state, onDownload = actions.onDownload)
        }
    }
}

@Composable
private fun ScanningState(phase: OfflinePhase.Scanning) {
    val progress = phase.progress
    Column(
        Modifier.fillMaxSize().padding(32.dp).widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        val walking = progress == null || progress.coursesDone < progress.courseCount
        if (walking) {
            val scanning = progress?.scanning.orEmpty()
            Text(
                when {
                    scanning.isEmpty() -> stringResource(R.string.offline_scanning, "…")
                    scanning.size == 1 -> stringResource(R.string.offline_scanning, scanning.first())
                    else -> pluralStringResource(R.plurals.offline_scanning_more, scanning.size - 1, scanning.first(), scanning.size - 1)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            val fraction = progress?.let { if (it.courseCount > 0) it.coursesDone / it.courseCount.toFloat() else null }
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val found = progress?.filesFound ?: 0
            Text(pluralStringResource(R.plurals.offline_files_found, found, found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.offline_checking_sizes, progress!!.sizesChecked, progress.filesFound),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { if (progress.filesFound > 0) progress.sizesChecked / progress.filesFound.toFloat() else 1f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.offline_scanning_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FileList(state: OfflineUiState, actions: OfflineActions) {
    val shown = if (state.hideDownloaded) state.files.filterNot { it.alreadyDownloaded } else state.files
    LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "options") {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val haptics = LocalHapticFeedback.current
                    TollyListItem(
                        modifier = Modifier.groupItem(ItemPosition.Single, role = Role.Switch, onClick = {
                            haptics.performHapticFeedback(if (state.copyToDownloads) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            actions.onCopyToDownloads(!state.copyToDownloads)
                        }),
                        headline = { Text(stringResource(R.string.offline_copy_to_downloads)) },
                        trailing = { TollySwitch(checked = state.copyToDownloads, onCheckedChange = null) },
                    )
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = withHaptic(HapticFeedbackType.SegmentTick, actions.onSelectAll),
                        label = { Text(stringResource(R.string.offline_select_all)) },
                    )
                    AssistChip(
                        onClick = withHaptic(HapticFeedbackType.SegmentTick, actions.onSelectNone),
                        label = { Text(stringResource(R.string.offline_select_none)) },
                    )
                    if (state.files.any { it.alreadyDownloaded }) {
                        FilterChip(
                            selected = state.hideDownloaded,
                            onClick = withHaptic(HapticFeedbackType.SegmentTick) { actions.onHideDownloaded(!state.hideDownloaded) },
                            label = { Text(stringResource(R.string.offline_hide_downloaded)) },
                        )
                    }
                }
            }
        }
        itemsIndexed(shown, key = { _, it -> it.request.key }) { index, file ->
            FileRow(
                file = file,
                position = ItemPosition(index, shown.size),
                selected = file.request.key in state.selected,
                showCourse = state.courseCount != 1,
                onToggle = { actions.onToggle(file) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun FileRow(
    file: OfflineFile,
    position: ItemPosition,
    selected: Boolean,
    showCourse: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val request = file.request
    TollyListItem(
        modifier = modifier
            .groupItem(
                position,
                enabled = !file.alreadyDownloaded,
                role = Role.Checkbox,
                onClick = {
                    haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle()
                },
                separatorStart = GroupDefaults.separatorStart(72.dp),
            )
            .alpha(if (file.alreadyDownloaded) 0.6f else 1f),
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (file.alreadyDownloaded) {
                    Icon(Icons.Outlined.DownloadDone, contentDescription = stringResource(R.string.download_status_done), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
                Icon(fileIcon(request.mimeType, request.fileName), contentDescription = null, modifier = Modifier.padding(start = 12.dp))
            }
        },
        headline = { Text(request.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = {
            val where = listOfNotNull(request.courseName?.takeIf { showCourse }, request.folderPath.takeIf { it.isNotBlank() }?.replace("/", " › "))
            if (where.isNotEmpty()) Text(where.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailing = {
            Text(
                file.size?.let { fileSize(it) } ?: stringResource(R.string.offline_unknown_size),
                style = MaterialTheme.typography.labelLarge,
                color = if (file.size == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun SummaryBar(state: OfflineUiState, onDownload: () -> Unit) {
    val count = state.selectedFiles.size
    val tooBig = state.freeBytes in 1 until state.selectedBytes
    Surface(color = Tolly.colors.group, shape = groupShape(ItemPosition.Single), modifier = Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.offline_selected, count, count, fileSize(state.selectedBytes)),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tooBig) Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(
                    if (tooBig) stringResource(R.string.offline_not_enough_space) else stringResource(R.string.offline_free_space, fileSize(state.freeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = withHaptic(HapticFeedbackType.Confirm, onDownload),
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pluralStringResource(R.plurals.offline_download_button, count, count))
            }
        }
    }
}
