package be.tolly.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.data.htmlToPlainText
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SwipeActions
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.highlightColor
import be.tolly.core.designsystem.component.relativeTime
import be.tolly.core.model.Announcement
import be.tolly.core.model.Highlight

/**
 * An [AnnouncementRow] that can be swiped: to the left to mark it read or unread (as in
 * Thunderbird), to the right to flag it orange.
 */
@Composable
fun SwipeableAnnouncementRow(
    announcement: Announcement,
    selected: Boolean,
    showCourse: Boolean,
    position: ItemPosition,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onHighlight: (Highlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeActions(
        position = position,
        startAction = highlightAction(announcement.highlight, Highlight.ORANGE) { onHighlight(Highlight.ORANGE) },
        endAction = readAction(announcement.isRead, onToggleRead),
        modifier = modifier,
    ) {
        AnnouncementRow(announcement, selected, showCourse, position, onClick)
    }
}

@Composable
fun AnnouncementRow(
    announcement: Announcement,
    selected: Boolean,
    showCourse: Boolean,
    position: ItemPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = remember(announcement.bodyHtml) { htmlToPlainText(announcement.bodyHtml, maxLength = 240) }
    TollyListItem(
        modifier = modifier.groupItem(
            position,
            selected = selected,
            onClick = onClick,
            separatorStart = GroupDefaults.separatorStart(if (showCourse) 40.dp else 0.dp),
            highlight = announcement.highlight?.let(::highlightColor) ?: Color.Unspecified,
        ),
        leading = if (showCourse) {
            {
                CourseAvatar(
                    name = announcement.courseName.orEmpty(),
                    seed = announcement.courseId,
                    size = 40.dp,
                    icon = announcement.courseIcon,
                    color = announcement.courseColor,
                )
            }
        } else {
            null
        },
        overline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showCourse) {
                    Text(
                        announcement.courseName.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("  ·  ")
                }
                Text(relativeTime(announcement.created), maxLines = 1)
            }
        },
        headline = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Centre the dot on the first line, not on a title that wraps.
                if (!announcement.isRead) {
                    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
                    UnreadDot(Modifier.padding(top = ((lineHeight - UnreadDotSize) / 2).coerceAtLeast(0.dp)))
                }
                // As in a mail app: unread stands out in bold, read steps back in the secondary colour.
                Text(
                    announcement.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (announcement.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = if (announcement.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        supporting = if (preview.isNotBlank()) {
            {
                Text(
                    preview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.let { if (announcement.isRead) it.copy(alpha = 0.7f) else it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    )
}
