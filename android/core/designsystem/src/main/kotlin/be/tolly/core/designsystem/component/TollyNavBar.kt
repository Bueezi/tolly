package be.tolly.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavBarHeight = 60.dp
private val NavBarMarginVertical = 10.dp

/** Keeps the bar clear of the screen's rounded corners. */
private val NavBarMarginHorizontal = 20.dp
private val NavBarPadding = 6.dp

/** Tabs without their label are all this wide (and, as the bar is inside its padding, as high): the smallest touch target. */
private val IconTabWidth = 48.dp

/** What scrolling content leaves free at the bottom for [TollyNavBar] (the bar plus its margins). */
val TollyNavBarSpace = NavBarHeight + NavBarMarginVertical * 2

/**
 * The Material look's navigation bar on phones, after ObtainX: a floating pill that content
 * scrolls under, with the selected tab as a filled capsule showing its icon and label, and the
 * other tabs as icons only. The capsule grows and shrinks as the selection moves.
 *
 * [icon] draws a tab's icon (with any badge); [label] is its name, also read out for the
 * icon-only tabs.
 */
@Composable
fun TollyNavBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (index: Int) -> String,
    modifier: Modifier = Modifier,
    icon: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = NavBarMarginHorizontal, vertical = NavBarMarginVertical),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier
                .height(NavBarHeight)
                .padding(NavBarPadding)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(itemCount) { index ->
                NavTab(
                    selected = index == selectedIndex,
                    label = label(index),
                    onClick = { onSelect(index) },
                ) { icon(index, index == selectedIndex) }
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(selected: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh.copy(alpha = 0f), label = "tab")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "tab content")
    Box(
        Modifier
            // The selected tab takes what's left after the icon tabs, never more than it needs.
            .then(if (selected) Modifier.weight(1f, fill = false) else Modifier.width(IconTabWidth))
            .height(NavBarHeight - NavBarPadding * 2)
            .clip(CircleShape)
            .background(container)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(),
            )
            .semantics { if (!selected) contentDescription = label }
            .padding(horizontal = if (selected) 14.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                ) {
                    val style = MaterialTheme.typography.titleSmall
                    Text(
                        label,
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
