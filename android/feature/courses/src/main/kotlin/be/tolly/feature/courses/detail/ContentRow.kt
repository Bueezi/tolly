package be.tolly.feature.courses.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.contentTypeIcon
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.highlightColor
import be.tolly.core.designsystem.component.relativeTime
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.ContentNode
import be.tolly.core.model.ContentType
import be.tolly.core.ui.DownloadStatusIcon
import be.tolly.core.ui.downloadStatusText
import be.tolly.core.ui.fileTypeLabel

@Composable
internal fun ContentRow(
    item: ContentItemUi,
    selected: Boolean,
    position: ItemPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Files only: the long-press menu (open with, share, save a copy). */
    onLongClick: (() -> Unit)? = null,
) {
    val node = item.node
    // Items listed under an expanded learning module are indented inside the group.
    val indent = if (item.module != null) ModuleIndent else 0.dp
    TollyListItem(
        modifier = modifier
            .groupItem(
                position,
                selected = selected,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.action_more) else null,
                separatorStart = indent + GroupDefaults.separatorStart(TypeIconSize),
                highlight = item.highlight?.let(::highlightColor) ?: Color.Unspecified,
            )
            .padding(start = indent)
            .alpha(if (node.available) 1f else 0.6f),
        leading = { TypeIcon(node) },
        headline = {
            Text(
                if (node.isPageBody) stringResource(R.string.content_page) else node.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = { Text(supportingText(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = { Trailing(item) },
    )
}

@Composable
private fun TypeIcon(node: ContentNode) {
    val isFolder = node.type.isContainer || node.isPage
    // iOS shows folders and files as tinted glyphs; Material as tonal tiles.
    if (Tolly.glass) {
        Box(Modifier.size(TypeIconSize), contentAlignment = Alignment.Center) {
            Icon(
                contentTypeIcon(if (node.isPage) ContentType.DOCUMENT else node.type, node.mimeType, node.fileName),
                contentDescription = null,
                tint = if (isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isFolder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (isFolder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(TypeIconSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                contentTypeIcon(if (node.isPage) ContentType.DOCUMENT else node.type, node.mimeType, node.fileName),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun supportingText(item: ContentItemUi): String {
    val node = item.node
    val parts = mutableListOf<String>()
    when {
        node.isPage -> parts += stringResource(R.string.content_type_page)
        else -> parts += stringResource(typeLabel(node.type))
    }
    if (node.type == ContentType.FILE) {
        (downloadStatusText(item.download) ?: fileTypeLabel(node.fileName))?.let { parts += it }
    }
    node.modified?.let { parts += relativeTime(it) }
    if (!node.available) parts += stringResource(R.string.content_hidden)
    return parts.joinToString(" · ")
}

@Composable
private fun Trailing(item: ContentItemUi) {
    val node = item.node
    when {
        item.expandsInline -> when {
            item.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            item.expanded -> Icon(Icons.Filled.ExpandLess, contentDescription = stringResource(R.string.action_collapse))
            else -> Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.action_expand))
        }
        node.type.isContainer && !node.isPage -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        node.type == ContentType.FILE -> DownloadStatusIcon(item.download, Modifier.size(24.dp))
        node.type == ContentType.DOCUMENT || node.isPage -> Unit
        else -> Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = stringResource(R.string.action_open_in_browser),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown under an expanded learning module without items. */
@Composable
internal fun EmptyModuleRow(position: ItemPosition, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.content_module_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .groupItem(position, separatorStart = ModuleIndent + 16.dp)
            .fillMaxWidth()
            .padding(start = ModuleIndent + 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
    )
}

/** Start padding of the items listed under an expanded learning module. */
private val ModuleIndent = 24.dp

private val TypeIconSize = 40.dp

internal fun typeLabel(type: ContentType): Int = when (type) {
    ContentType.FOLDER -> R.string.content_type_folder
    ContentType.LESSON -> R.string.content_type_lesson
    ContentType.DOCUMENT -> R.string.content_type_document
    ContentType.FILE -> R.string.content_type_file
    ContentType.LINK -> R.string.content_type_link
    ContentType.LTI -> R.string.content_type_tool
    ContentType.ASSIGNMENT -> R.string.content_type_assignment
    ContentType.TEST -> R.string.content_type_test
    ContentType.DISCUSSION -> R.string.content_type_discussion
    ContentType.OTHER -> R.string.content_type_other
}
