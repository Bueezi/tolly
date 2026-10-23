package be.tolly.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LabelOff
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.SwipeAction
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.highlightColor
import be.tolly.core.model.Highlight

/**
 * The swipe that marks a row with [highlight], or takes the mark off again when the row has
 * that one already ([current]): a flag, or a crossed-out label to clear it.
 */
@Composable
fun highlightAction(current: Highlight?, highlight: Highlight, onSwipe: () -> Unit): SwipeAction {
    val clearing = current == highlight
    return SwipeAction(
        icon = if (clearing) Icons.Outlined.LabelOff else Icons.Outlined.Flag,
        label = stringResource(if (clearing) R.string.swipe_clear_mark else R.string.swipe_mark_orange),
        color = if (clearing) TileColor.Gray.ios else highlightColor(highlight),
        onSwipe = onSwipe,
    )
}

/** The swipe that marks an announcement read, or unread again, like in Thunderbird. */
@Composable
fun readAction(isRead: Boolean, onSwipe: () -> Unit): SwipeAction = SwipeAction(
    icon = if (isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
    label = stringResource(if (isRead) R.string.action_mark_unread else R.string.action_mark_read),
    color = TileColor.Blue.ios,
    onSwipe = onSwipe,
)
