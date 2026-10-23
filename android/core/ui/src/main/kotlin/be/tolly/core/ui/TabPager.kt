package be.tolly.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import be.tolly.core.designsystem.component.RowSwipes
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

/**
 * A pager over [count] tabs that follows [selected] (a tap on a segment scrolls there) and
 * reports the tab a swipe settles on through [onSelect]. Only a settled page is reported, so
 * the screen's state isn't rewritten mid-swipe. A swipe past halfway ticks, as the segments do.
 *
 * The page isn't saved with the screen's state: [selected] is the one truth, and a restored page
 * that disagreed with it was reported as a swipe, which put a course back on an old tab.
 */
@Composable
fun rememberTabPager(count: Int, selected: Int, onSelect: (Int) -> Unit): PagerState {
    val pager = remember { PagerState(currentPage = selected) { count } }
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(selected) { if (pager.targetPage != selected) pager.animateScrollToPage(selected) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != currentSelected) currentOnSelect(page) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.targetPage }.collect { page ->
            if (pager.isScrollInProgress && page != currentSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
    return pager
}

/** Where the pager is between its pages, e.g. 1.5 halfway from the second to the third. */
fun PagerState.position(): Float = currentPage + currentPageOffsetFraction

/**
 * How a swipe between tabs ends. The stock pager decides by the finger's speed when it lifts, so
 * a swipe that drifted back a hair at the end (a small speed the other way) snapped back to where
 * it started. Here the distance decides: past [COMMIT] of a page the swipe goes through unless
 * flung back hard, and a short swipe goes through when flicked on.
 */
@Composable
fun rememberTabFling(pager: PagerState): TargetedFlingBehavior {
    val density = LocalDensity.current
    return remember(pager, density) { TabFling(pager, density) }
}

private class TabFling(private val pager: PagerState, density: Density) : TargetedFlingBehavior {
    private val flick = FLICK_DP_PER_S * density.density
    private val flingBack = FLING_BACK_DP_PER_S * density.density

    override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
        val info = pager.layoutInfo
        val pageSize = (info.pageSize + info.pageSpacing).toFloat()
        if (pageSize <= 0f) return initialVelocity
        val position = pager.position()
        val target = targetPage(position, initialVelocity).coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0))

        val distance = (target - position) * pageSize
        // Speed the other way (the drift that used to cancel the swipe) isn't carried over.
        val velocity = if (initialVelocity * distance > 0) initialVelocity else 0f
        var done = 0f
        animate(0f, distance, initialVelocity = velocity, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { value, _ ->
            scrollBy(value - done)
            done = value
            onRemainingDistanceUpdated(distance - value)
        }
        return 0f
    }

    private fun targetPage(position: Float, velocity: Float): Int {
        // The page the swipe started from: settledPage only moves once scrolling stops.
        val moved = position - pager.settledPage
        // Hardly moved: a quick flick still turns the page.
        if (abs(moved) < STILL) return pager.settledPage + if (abs(velocity) > flick) sign(velocity).toInt() else 0
        val onwards = velocity * moved >= 0
        val through = when {
            !onwards && abs(velocity) > flingBack -> false
            abs(moved) >= COMMIT -> true
            else -> onwards && abs(velocity) > flick
        }
        val ahead = if (moved > 0) ceil(position) else floor(position)
        val behind = if (moved > 0) floor(position) else ceil(position)
        return (if (through) ahead else behind).toInt()
    }
}

/** Moved less than this (of a page): a tap-like swipe, decided by speed alone. */
private const val STILL = 0.02f

/**
 * Moved this far (of a page), a swipe goes through unless flung back. Low, as a thumb's swipe is
 * short: at a third of the page, most thumb swipes fell back.
 */
private const val COMMIT = 0.15f

private const val FLICK_DP_PER_S = 200f
private const val FLING_BACK_DP_PER_S = 1500f

/**
 * The swipe between the pages of [pager], decided before anything on the pages sees it: a thumb's
 * swipe arcs, so the list under it often passed its (vertical) touch threshold first and took the
 * gesture, and rows with swipe actions took every horizontal one. Here a movement that is mostly
 * sideways (up to about 55° from level) turns the page; anything steeper is left to the list. A
 * row that can be swiped that way (to highlight it, say) keeps the swipe: provide [rowSwipes] as
 * `LocalRowSwipes` around the pages so the rows can say so. Give the pager
 * `userScrollEnabled = false`, so it doesn't also handle the drag itself.
 */
@Composable
fun Modifier.pageSwipe(pager: PagerState, rowSwipes: RowSwipes? = null): Modifier {
    val fling = rememberTabFling(pager)
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(pager, rtl, rowSwipes) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var moved = Offset.Zero
            var drag: Channel<Float>? = null
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                velocity.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                if (drag == null) {
                    moved += delta
                    if (abs(moved.y) > slop && abs(moved.y) * SIDEWAYS > abs(moved.x)) break // the list scrolls
                    if (abs(moved.x) <= slop || abs(moved.x) * SIDEWAYS < abs(moved.y)) continue
                    if (rowSwipes?.rowTakes(down.id, moved.x) == true) break // the row's own swipe
                    // The pager's scroll lasts the whole drag and fling, so settledPage stays the
                    // page it started on (which the fling decides by).
                    val deltas = Channel<Float>(Channel.UNLIMITED).also { drag = it }
                    scope.launch {
                        pager.scroll {
                            for (d in deltas) {
                                if (d.isNaN()) break
                                scrollBy(d)
                            }
                            val v = deltas.tryReceive().getOrNull() ?: 0f
                            with(fling) { performFling(v) }
                        }
                    }
                    deltas.trySend(toScroll(moved.x, rtl))
                } else {
                    drag?.trySend(toScroll(delta.x, rtl))
                }
                change.consume()
            }
            drag?.let {
                it.trySend(Float.NaN)
                it.trySend(toScroll(velocity.calculateVelocity().x, rtl))
                it.close()
            }
        }
    }
}

/** A sideways finger movement as the pager's scroll: moving towards the start edge goes forward. */
private fun toScroll(x: Float, rtl: Boolean) = if (rtl) x else -x

/** How much more sideways than up or down a movement must be to turn the page (tan of ~55°). */
private const val SIDEWAYS = 0.7f

/**
 * Lets a swipe on this element (a tab header, which has no rows to compete with) move the
 * [pager] too, flinging and snapping like a swipe on the pages themselves.
 */
@Composable
fun Modifier.swipeTabs(pager: PagerState): Modifier {
    // The pager scrolls forward as the finger moves towards the start edge, which `scrollable`
    // takes for the opposite of its default (it reverses horizontal scrolling by itself in RTL).
    val reverse = LocalLayoutDirection.current == LayoutDirection.Ltr
    return scrollable(pager, Orientation.Horizontal, reverseDirection = reverse, flingBehavior = rememberTabFling(pager))
}
