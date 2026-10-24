package be.tolly.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.component.TollyBackButton

/** How a pane offers to go back: an arrow when it covers the previous pane, a close button beside it. */
enum class PaneNavigation { NONE, BACK, CLOSE }

/**
 * The pane's way back. Beside another pane it also gets the button that folds that pane away:
 * alone next to the list (no way back), before the close button next to a course.
 */
@Composable
fun PaneNavigationIcon(navigation: PaneNavigation, onClick: () -> Unit) {
    val toggle = LocalListPaneToggle.current
    when (navigation) {
        PaneNavigation.NONE -> toggle?.let { ListPaneToggleButton(it) }
        PaneNavigation.BACK -> TollyBackButton(onClick = onClick)
        PaneNavigation.CLOSE -> Row(verticalAlignment = Alignment.CenterVertically) {
            toggle?.let { ListPaneToggleButton(it) }
            TollyBackButton(onClick = onClick, close = true)
        }
    }
}

internal val UnreadDotSize = 10.dp

/** Small dot marking unread items. */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(UnreadDotSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}
