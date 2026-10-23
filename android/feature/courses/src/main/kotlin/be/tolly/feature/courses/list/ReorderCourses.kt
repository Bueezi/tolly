package be.tolly.feature.courses.list

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.groupShape
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.Course
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * The course list in reorder mode: each section (title to courses) can be rearranged by dragging
 * a row's handle, or by long-pressing the row. Courses never move between sections.
 * [onOrderChanged] gets a section's course ids after each drop.
 */
@Composable
internal fun ReorderCourseList(
    sections: List<Pair<Int, List<Course>>>,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // The drafts follow the user's drags; they start from (and reset to) the saved order.
    var drafts by remember(sections) { mutableStateOf(sections.map { it.second }) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val section = drafts.indexOfFirst { list -> list.any { it.id == from.key } }
        val list = drafts.getOrNull(section) ?: return@rememberReorderableLazyListState
        val fromIndex = list.indexOfFirst { it.id == from.key }
        val toIndex = list.indexOfFirst { it.id == to.key }
        if (toIndex < 0) return@rememberReorderableLazyListState // a header or another section
        drafts = drafts.toMutableList().also { all ->
            all[section] = list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current)) {
        item(key = "hint") {
            Text(
                stringResource(R.string.reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        sections.forEachIndexed { index, (title, _) ->
            val courses = drafts.getOrNull(index).orEmpty()
            if (courses.isEmpty()) return@forEachIndexed
            item(key = "header-$title") { SectionHeader(stringResource(title)) }
            itemsIndexed(courses, key = { _, course -> course.id }) { position, course ->
                ReorderableItem(reorderState, key = course.id) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "drag elevation")
                    val onDragStarted: (Offset) -> Unit = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    val onDragStopped: () -> Unit = {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        onOrderChanged(drafts[index].map { it.id })
                    }
                    // A dragged row lifts out of its group as a card of its own.
                    val itemPosition = ItemPosition(position, courses.size)
                    Surface(
                        shadowElevation = elevation,
                        shape = groupShape(if (dragging) ItemPosition.Single else itemPosition),
                        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Tolly.colors.group,
                        modifier = Modifier
                            .padding(
                                start = GroupDefaults.Margin,
                                end = GroupDefaults.Margin,
                                bottom = if (Tolly.glass || itemPosition.isLast) 0.dp else 2.dp,
                            )
                            .longPressDraggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped),
                    ) {
                        TollyListItem(
                            leading = {
                                CourseAvatar(name = course.displayName, seed = course.id, icon = course.icon, color = course.color)
                            },
                            headline = { Text(course.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailing = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped),
                                ) {
                                    Icon(Icons.Filled.DragHandle, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
