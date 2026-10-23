package be.tolly.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The divider between two panes side by side: how wide the first pane is, and what dragging the
 * divider does to it. The pane keeps a readable width ([MinPaneWidth] and up, at most
 * [MAX_FRACTION] of the window); drag it narrower than that and, when it [canFold], it snaps shut
 * like a magnet, and drag back out and it snaps open again, so it is never squashed. Where the
 * finger lets go, it stays.
 */
@Stable
internal class PaneDivider(
    private val scope: CoroutineScope,
    private val canFold: Boolean,
    initialWidth: Float,
    openWidth: Float,
) {
    /** The width the pane had when it was last open, in dp, to unfold to. */
    var openWidth = openWidth
        private set

    /** The pane's width in dp, animated when it snaps; the spring may not take it below 0. */
    val width = Animatable(initialWidth).apply { updateBounds(lowerBound = 0f) }

    /** The window's width in dp, to keep the other pane room. */
    private var total by mutableFloatStateOf(0f)

    /** Where the finger puts the divider, which may be inside the magnet. */
    private var dragTo = 0f

    val folded: Boolean get() = width.targetValue == 0f

    val current: Dp get() = width.value.dp

    private val maxWidth: Float get() = if (total > 0f) maxOf(MinPaneWidth.value, total * MAX_FRACTION) else Float.MAX_VALUE

    fun measured(totalWidth: Dp) {
        total = totalWidth.value
        // A window that got narrower takes the pane along.
        if (width.targetValue > maxWidth) scope.launch { width.snapTo(maxWidth) }
    }

    fun dragStarted() {
        dragTo = width.targetValue
    }

    /** Moves the divider by [delta] dp; returns true when that snapped the pane shut or open. */
    fun drag(delta: Float): Boolean {
        dragTo = (dragTo + delta).coerceIn(0f, maxWidth)
        val target = when {
            dragTo >= MinPaneWidth.value -> dragTo
            canFold -> 0f
            else -> MinPaneWidth.value
        }
        val snapped = (target == 0f) != folded
        scope.launch { if (snapped || width.isRunning) width.animateTo(target, MagnetSpring) else width.snapTo(target) }
        return snapped
    }

    fun dragStopped() {
        if (!folded) openWidth = width.targetValue
    }

    /** Folds the pane away, or brings it back at the width it had. */
    fun toggle() {
        val target = if (folded) openWidth.coerceIn(MinPaneWidth.value, maxWidth) else 0f
        scope.launch { width.animateTo(target, MagnetSpring) }
    }

    companion object {
        /** Narrower than this a list is squashed: the pane snaps shut instead. */
        val MinPaneWidth = 280.dp

        /** What the pane opens at the first time: about the width Material suggests for a list. */
        val DefaultPaneWidth = 360.dp

        /** How much of the window the first pane may take; the pane beside it needs the rest. */
        const val MAX_FRACTION = 0.65f

        private val MagnetSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    }
}

/** A [PaneDivider] that survives configuration changes; [key] tells apart which panes it divides. */
@Composable
internal fun rememberPaneDivider(key: Any, canFold: Boolean): PaneDivider {
    val scope = rememberCoroutineScope()
    return rememberSaveable(
        key,
        canFold,
        saver = listSaver(
            save = { listOf(it.width.targetValue, it.openWidth) },
            restore = { (width, open) -> PaneDivider(scope, canFold, width, open) },
        ),
    ) {
        PaneDivider(scope, canFold, PaneDivider.DefaultPaneWidth.value, PaneDivider.DefaultPaneWidth.value)
    }
}
