package be.tolly.core.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable

/**
 * The default pane directive, but never more than two panes side by side: list + detail, or
 * detail + opened item. A third, mostly empty column on very wide windows wastes space.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberTwoPaneDirective(): PaneScaffoldDirective {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    return if (directive.maxHorizontalPartitions <= 2) directive else directive.copy(maxHorizontalPartitions = 2)
}
