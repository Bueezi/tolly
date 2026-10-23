package be.tolly.feature.courses.document

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.UltraEndpoints
import be.tolly.core.data.DataError
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.HtmlContent
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.LoadingState
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.fileIcon
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.ui.DownloadStatusIcon
import be.tolly.core.ui.FileOptionsSheet
import be.tolly.core.ui.FileTile
import be.tolly.core.ui.HandleFileEvents
import be.tolly.core.ui.ListMessageState
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import be.tolly.core.ui.RefreshErrorBanner
import be.tolly.core.ui.dataErrorMessage
import be.tolly.core.ui.downloadStatusText
import be.tolly.core.ui.fileTypeLabel
import be.tolly.core.ui.openInBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPane(
    courseId: String,
    contentId: String,
    isPage: Boolean,
    title: String,
    navigation: PaneNavigation,
    onNavigate: () -> Unit,
) {
    val viewModel = hiltViewModel<DocumentViewModel, DocumentViewModel.Factory>(key = "doc:$courseId:$contentId") {
        it.create(courseId, contentId, isPage)
    }
    LaunchedEffect(viewModel) { viewModel.onOpened() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    HandleFileEvents(viewModel.files.events, snackbarHostState)
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }

    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            TollyTopBar(
                large = false,
                title = { Text(state.node?.title ?: title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
                actions = {
                    state.node?.let { node ->
                        IconButton(onClick = { context.openInBrowser(node.webUrl) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.action_open_in_toledo))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            RefreshErrorBanner(error, hasContent = state.bodyHtml != null, onRetry = viewModel::refresh)
            val html = state.bodyHtml
            val preview = state.preview
            val bottomInset = LocalBottomBarInset.current
            val htmlPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset)
            when {
                // Only files, nothing to read: the files are the page.
                html != null && !state.hasText && preview == null -> FileList(
                    files = state.files,
                    onOpen = { viewModel.openFile(it.file) },
                    onOptions = { optionsFor = it.file.url },
                    bottomInset = bottomInset,
                )
                // Only one HTML file: show what's in it.
                html != null && preview != null -> {
                    FileChips(files = state.files, onClick = { viewModel.openFile(it.file) }, onLongClick = { optionsFor = it.file.url })
                    HorizontalDivider()
                    when (preview) {
                        HtmlPreview.Loading -> LoadingState()
                        is HtmlPreview.Ready -> HtmlContent(
                            html = preview.html,
                            onLinkClick = viewModel::onLink,
                            baseUrl = UltraEndpoints.BASE_URL,
                            resourceLoader = viewModel::loadResource,
                            contentPadding = htmlPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                        HtmlPreview.Failed -> MessageState(
                            icon = fileIcon(state.files.firstOrNull()?.file?.mimeType, state.files.firstOrNull()?.file?.displayName),
                            title = stringResource(R.string.document_preview_failed),
                        )
                    }
                }
                html != null -> {
                    if (state.files.isNotEmpty()) {
                        FileChips(files = state.files, onClick = { viewModel.openFile(it.file) }, onLongClick = { optionsFor = it.file.url })
                        HorizontalDivider()
                    }
                    HtmlContent(
                        html = html,
                        onLinkClick = viewModel::onLink,
                        baseUrl = UltraEndpoints.BASE_URL,
                        resourceLoader = viewModel::loadResource,
                        contentPadding = htmlPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.loading -> LoadingState()
                error == DataError.SESSION_EXPIRED -> ListMessageState(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    emptyTitle = "",
                    error = error,
                    onRetry = viewModel::refresh,
                )
                else -> MessageState(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = stringResource(if (error != null) dataErrorMessage(error) else R.string.document_empty),
                    actionLabel = stringResource(R.string.action_open_in_toledo),
                    onAction = { state.node?.let { context.openInBrowser(it.webUrl) } },
                )
            }
        }
    }

    val options = optionsFor?.let { url -> state.files.firstOrNull { it.file.url == url } }
    if (options != null) {
        FileOptionsSheet(
            name = options.file.displayName,
            mimeType = options.file.mimeType,
            download = options.download,
            onAction = { action -> viewModel.act(options.file, action) },
            onDismiss = { optionsFor = null },
        )
    }
}

/** A page's files when that's all it holds: a grouped list, every file in full view. */
@Composable
private fun FileList(files: List<EmbeddedFileUi>, onOpen: (EmbeddedFileUi) -> Unit, onOptions: (EmbeddedFileUi) -> Unit, bottomInset: Dp) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 0.dp, bottom = 16.dp + bottomInset)) {
        item(key = "header") { SectionHeader(stringResource(R.string.document_files)) }
        itemsIndexed(files, key = { _, it -> it.file.url }) { index, item ->
            TollyListItem(
                modifier = Modifier.groupItem(
                    ItemPosition(index, files.size),
                    onClick = { onOpen(item) },
                    onLongClick = { onOptions(item) },
                    onLongClickLabel = stringResource(R.string.action_more),
                    separatorStart = GroupDefaults.separatorStart(40.dp),
                ),
                leading = { FileTile(item.file.mimeType, item.file.displayName) },
                headline = { Text(item.file.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supporting = {
                    val status = listOfNotNull(fileTypeLabel(item.file.displayName), downloadStatusText(item.download)).joinToString(" · ")
                    Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailing = { DownloadStatusIcon(item.download, Modifier.size(24.dp), strokeWidth = 2.dp) },
            )
        }
    }
}

/**
 * A document's files above its text, wrapping onto as many lines as they need instead of
 * scrolling sideways. Past [MAX_COLLAPSED_FILES] the rest folds into a "more" chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileChips(files: List<EmbeddedFileUi>, onClick: (EmbeddedFileUi) -> Unit, onLongClick: (EmbeddedFileUi) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val folds = files.size > MAX_COLLAPSED_FILES
    val shown = if (folds && !expanded) files.take(MAX_COLLAPSED_FILES - 1) else files
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { item ->
            FileChip(
                label = item.file.displayName,
                icon = fileIcon(item.file.mimeType, item.file.displayName),
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
            ) { DownloadStatusIcon(item.download, Modifier.size(AssistChipDefaults.IconSize), showDownload = false, strokeWidth = 2.dp) }
        }
        if (folds) {
            val haptics = LocalHapticFeedback.current
            FileChip(
                label = if (expanded) {
                    stringResource(R.string.document_show_fewer)
                } else {
                    pluralStringResource(R.plurals.document_more_files, files.size - shown.size, files.size - shown.size)
                },
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                onClick = {
                    haptics.performHapticFeedback(if (expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    expanded = !expanded
                },
            )
        }
    }
}

/** An outlined chip, like an assist chip, that also takes a long press. */
@Composable
private fun FileChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { stringResource(R.string.action_more) })
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AssistChipDefaults.IconSize))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 260.dp),
        )
        trailing?.invoke()
    }
}

/** More files than this fold into a "more" chip above a document's text. */
private const val MAX_COLLAPSED_FILES = 6
