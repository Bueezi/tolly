package be.tolly.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.theme.Tolly
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Which ways the row under the latest touch can be swiped, for a pager around rows: it leaves a
 * swipe that way to the row (see `pageSwipe` in core/ui). Rows report the touch as it goes down.
 */
class RowSwipes {
    var pointer: PointerId? = null
        private set
    var rightward = false
        private set
    var leftward = false
        private set

    internal fun touched(id: PointerId, right: Boolean, left: Boolean) {
        pointer = id
        rightward = right
        leftward = left
    }

    /** Whether the row under [id] takes a swipe towards [x] (positive is rightwards). */
    fun rowTakes(id: PointerId, x: Float): Boolean = pointer == id && if (x > 0) rightward else leftward
}

/** The [RowSwipes] of the pager around these rows, if any. */
val LocalRowSwipes = staticCompositionLocalOf<RowSwipes?> { null }

/** What swiping a row one way does: the icon and colour shown behind the row, and the action. */
@Immutable
class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val onSwipe: () -> Unit)

/**
 * A grouped row (built with [groupItem]) with an action for each swipe direction, like iOS Mail:
 * [startAction] when swiped from the start edge towards the end (rightwards in LTR, leftwards in
 * RTL), [endAction] the other way.
 * Crossing the threshold ticks; letting go there runs the action once and the row springs back.
 * A swipe in a direction without an action isn't taken, so it reaches what's around the row (the
 * pager between a course's tabs). A gesture that ends any other way than the finger lifting (the
 * system's back-edge swipe, a parent taking the pointer) runs nothing.
 * TalkBack users get both actions as custom actions.
 */
@Composable
fun SwipeActions(
    position: ItemPosition,
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rightwardAction = if (rtl) endAction else startAction
    val leftwardAction = if (rtl) startAction else endAction
    val currentRightward by rememberUpdatedState(rightwardAction)
    val currentLeftward by rememberUpdatedState(leftwardAction)
    // How far the row is pulled, in pixels: positive is rightwards.
    var pull by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // The spring back of the last swipe, which a new swipe on the row must stop, or it would keep
    // pulling the row back to rest under the finger and the swipe would end short of its threshold.
    var springBack by remember { mutableStateOf<Job?>(null) }
    val armed by remember { derivedStateOf { width > 0 && abs(pull) >= width * SWIPE_THRESHOLD } }
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }

    val glass = Tolly.glass
    val rowSwipes = LocalRowSwipes.current
    Box(
        modifier
            .groupFrame(position, glass)
            .clip(groupShape(position))
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                fun allowed(towards: Float) = if (towards > 0) currentRightward != null else currentLeftward != null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    rowSwipes?.touched(down.id, right = allowed(1f), left = allowed(-1f))
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    // Only a pull towards an action is ours; anything else is left unconsumed.
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (allowed(overSlop)) {
                            springBack?.cancel()
                            change.consume()
                            pull = overSlop
                        }
                    } ?: return@awaitEachGesture
                    val lifted = horizontalDrag(drag.id) { change ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        val next = (pull + change.positionChange().x).coerceIn(-width.toFloat(), width.toFloat())
                        // Back past the middle only goes as far as there's an action that way.
                        pull = if (next == 0f || allowed(next)) next else 0f
                        change.consume()
                    }
                    val released = pull
                    // A short, quick flick counts as well as a long, slow pull.
                    val flung = abs(velocity.calculateVelocity().x) >= FLICK_VELOCITY.dp.toPx() &&
                        released * velocity.calculateVelocity().x > 0 && abs(released) >= width * FLICK_MIN_PULL
                    if (lifted && width > 0 && (abs(released) >= width * SWIPE_THRESHOLD || flung)) {
                        (if (released > 0) currentRightward else currentLeftward)?.onSwipe?.invoke()
                    }
                    springBack = scope.launch {
                        animate(released, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> pull = value }
                    }
                }
            }
            .semantics {
                customActions = listOfNotNull(startAction, endAction).map { action ->
                    CustomAccessibilityAction(action.label) {
                        action.onSwipe()
                        true
                    }
                }
            },
    ) {
        val action = when {
            pull > 0f -> rightwardAction
            pull < 0f -> leftwardAction
            else -> null
        }
        val iconScale by animateFloatAsState(if (armed) 1.15f else 0.85f, label = "swipe icon")
        // A rightwards pull uncovers the left edge, which is the start edge only in LTR.
        if (action != null) SwipeBackground(action, armed, iconScale, fromStart = (pull > 0f) != rtl, Modifier.matchParentSize())
        Box(Modifier.graphicsLayer { translationX = pull }) {
            CompositionLocalProvider(LocalInGroupFrame provides true) { content() }
        }
    }
}

@Composable
private fun SwipeBackground(action: SwipeAction, armed: Boolean, iconScale: Float, fromStart: Boolean, modifier: Modifier) {
    Row(
        modifier
            .background(if (armed) action.color else action.color.copy(alpha = 0.55f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
    ) {
        Box(Modifier.scale(iconScale)) { Icon(action.icon, contentDescription = null, tint = Color.White) }
    }
}

/** How far (of the row's width) a swipe has to go to count. */
private const val SWIPE_THRESHOLD = 0.25f

/** A flick faster than this (dp per second) counts after [FLICK_MIN_PULL] of the width. */
private const val FLICK_VELOCITY = 700
private const val FLICK_MIN_PULL = 0.1f
