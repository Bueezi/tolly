package be.tolly.core.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.theme.Tolly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lets a detail pane fold the list beside it away, and bring it back. */
@Immutable
class ListPaneToggle(val collapsed: Boolean, val toggle: () -> Unit)

/** The [ListPaneToggle] for the pane next to the list, while both are shown. */
val LocalListPaneToggle = staticCompositionLocalOf<ListPaneToggle?> { null }

/**
 * A list | detail | extra layout driven by [navigator], for the Courses and Announcements tabs.
 * Each pane gets the navigator's content key it is shown for.
 *
 * - One pane at a time (phones): a stack of screens with [ScreenTransitions]. The back gesture
 *   previews the screen underneath all the way, under the finger. The library's scaffold only
 *   plays a tenth of its transition there, and draws the screen coming back in front of the one
 *   leaving, so its fade shows through the page being swiped away.
 * - Two panes side by side: the library's scaffold, with a [PaneDivider] between them that resizes
 *   the first one (the list, or the course beside an opened item), and pulls it shut like a magnet
 *   when it gets too narrow; a button in the second pane folds it away and back (see
 *   [LocalListPaneToggle]). The divider can be grabbed anywhere along its height.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> TollyListDetailScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    list: @Composable (key: T?) -> Unit,
    detail: @Composable (key: T?) -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable (key: T?) -> Unit)? = null,
) {
    if (navigator.scaffoldDirective.maxHorizontalPartitions <= 1) {
        PaneStack(navigator, list, detail, extra, modifier)
    } else {
        SidePanes(navigator, list, detail, extra, modifier)
    }
}

/** Which pane fills the screen when there's room for one. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun ThreePaneScaffoldValue.shownRole(): ThreePaneScaffoldRole = when {
    this[ListDetailPaneScaffoldRole.Extra] == PaneAdaptedValue.Expanded -> ListDetailPaneScaffoldRole.Extra
    this[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded -> ListDetailPaneScaffoldRole.Detail
    else -> ListDetailPaneScaffoldRole.List
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val ThreePaneScaffoldRole.depth: Int
    get() = when (this) {
        ListDetailPaneScaffoldRole.List -> 0
        ListDetailPaneScaffoldRole.Detail -> 1
        else -> 2
    }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun <T> PaneStack(
    navigator: ThreePaneScaffoldNavigator<T>,
    list: @Composable (key: T?) -> Unit,
    detail: @Composable (key: T?) -> Unit,
    extra: (@Composable (key: T?) -> Unit)?,
    modifier: Modifier,
) {
    val role = navigator.scaffoldValue.shownRole()
    val key = navigator.currentDestination?.contentKey
    // A screen keeps the key it was last shown with, so while it slides away after going back it
    // still shows what it showed (not an empty "select a course").
    val keys = remember { HashMap<ThreePaneScaffoldRole, T?>() }
    keys[role] = key
    val screens = remember { SeekableTransitionState(role) }
    LaunchedEffect(role) { if (screens.currentState != role || screens.targetState != role) screens.animateTo(role) }

    // Composed before the screens, so their own back handlers (leaving a folder, closing a
    // search) go first.
    PredictiveBackHandler(enabled = navigator.canNavigateBack(BackBehavior)) { events ->
        val to = navigator.peekPreviousScaffoldValue(BackBehavior).shownRole()
        if (to == role) {
            // Back changes what the shown pane shows, not which pane: nothing to preview.
            events.collect {}
            navigator.navigateBack(BackBehavior)
        } else {
            screens.followBack(events, from = role, to = to) { navigator.navigateBack(BackBehavior) }
        }
    }

    val saveable = rememberSaveableStateHolder()
    rememberTransition(screens, label = "panes").AnimatedContent(
        modifier = modifier,
        transitionSpec = { ScreenTransitions.between(deeper = targetState.depth > initialState.depth) },
    ) { shown ->
        val shownKey = if (shown == role) key else keys[shown] ?: key
        // The list keeps its state (scroll position) throughout; a detail screen keeps one per
        // item, or opening course B would scroll to where course A was left.
        val stateKey = if (shown == ListDetailPaneScaffoldRole.List) "list" else "${shown.depth}:$shownKey"
        saveable.SaveableStateProvider(stateKey) {
            // Opaque, so the screen underneath doesn't show through between the rows on top.
            Box(Modifier.fillMaxSize().background(Tolly.colors.screen)) {
                when (shown) {
                    ListDetailPaneScaffoldRole.List -> list(shownKey)
                    ListDetailPaneScaffoldRole.Detail -> detail(shownKey)
                    else -> extra?.invoke(shownKey)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun <T> SidePanes(
    navigator: ThreePaneScaffoldNavigator<T>,
    list: @Composable (key: T?) -> Unit,
    detail: @Composable (key: T?) -> Unit,
    extra: (@Composable (key: T?) -> Unit)?,
    modifier: Modifier,
) {
    val key = navigator.currentDestination?.contentKey
    PredictiveBackHandler(enabled = navigator.canNavigateBack(BackBehavior)) { events ->
        followBackGesture(
            events,
            seek = { navigator.seekBack(BackBehavior, fraction = it) },
            onBack = { navigator.navigateBack(BackBehavior) },
        )
    }

    val value = navigator.scaffoldValue
    val listBesideDetail = value[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        value[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val detailBesideExtra = value[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded &&
        value[ListDetailPaneScaffoldRole.Extra] == PaneAdaptedValue.Expanded
    // Either first pane folds away; the pane beside it has the button that brings it back.
    val canFold = listBesideDetail || detailBesideExtra
    val divider = rememberPaneDivider(key = listBesideDetail, canFold = canFold)
    val expansion = rememberPaneExpansionState(keyProvider = value)
    val density = LocalDensity.current
    LaunchedEffect(expansion, divider) {
        snapshotFlow { with(density) { divider.current.roundToPx().coerceAtLeast(0) } }.collect(expansion::setFirstPaneWidth)
    }
    val collapsed = divider.folded
    val toggle = remember(divider, collapsed) { ListPaneToggle(collapsed, divider::toggle) }
    val haptics = LocalHapticFeedback.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val toggleLabel = stringResource(if (collapsed) R.string.action_show_list else R.string.action_hide_list)

    CompositionLocalProvider(LocalListPaneToggle provides toggle.takeIf { canFold }) {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            listPane = { AnimatedPane(boundsAnimationSpec = PaneBounds) { list(key) } },
            detailPane = { AnimatedPane(boundsAnimationSpec = PaneBounds) { detail(key) } },
            extraPane = extra?.let { pane -> { AnimatedPane(boundsAnimationSpec = PaneBounds) { pane(key) } } },
            modifier = modifier.onSizeChanged { divider.measured(with(density) { it.width.toDp() }) },
            paneExpansionState = expansion,
            paneExpansionDragHandle = {
                val interactionSource = remember { MutableInteractionSource() }
                // The handle only shows where to grab: the whole strip between the panes drags, so
                // the finger doesn't have to find a 48 dp spot halfway down a tablet. Folded, there
                // is no gap for a strip: it would lie over the other pane's buttons.
                Box(
                    modifier = Modifier
                        .then(if (collapsed) Modifier else Modifier.fillMaxHeight().width(DragStripWidth))
                        .draggable(
                            state = rememberDraggableState { delta ->
                                val snapped = divider.drag(with(density) { (delta * direction).toDp().value })
                                if (snapped) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            orientation = Orientation.Horizontal,
                            interactionSource = interactionSource,
                            startDragImmediately = true,
                            onDragStarted = { divider.dragStarted() },
                            onDragStopped = { divider.dragStopped() },
                        )
                        .semantics {
                            if (canFold) {
                                onClick(toggleLabel) {
                                    divider.toggle()
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    VerticalDragHandle(interactionSource = interactionSource)
                }
            },
        )
    }
}

/** The detail pane's button that folds the list beside it away, or brings it back. */
@Composable
internal fun ListPaneToggleButton(toggle: ListPaneToggle) {
    IconButton(onClick = withHaptic(HapticFeedbackType.ToggleOn, toggle.toggle)) {
        Icon(
            if (toggle.collapsed) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.MenuOpen,
            contentDescription = stringResource(if (toggle.collapsed) R.string.action_show_list else R.string.action_hide_list),
        )
    }
}

/** How wide the strip between two panes is that drags the divider. */
private val DragStripWidth = 32.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

private val PaneSlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val PANE_SLIDE_MS = 400

private val PaneSlide = tween<IntOffset>(PANE_SLIDE_MS, easing = PaneSlideEasing)
private val PaneFade = tween<Float>(PANE_SLIDE_MS, easing = PaneSlideEasing)

/**
 * How one screen replaces another (screens on phones, folders in a course), after RikkaHub: the
 * new screen slides in over the old one, which moves half a screen away while it shrinks and
 * fades. All parts share one duration and easing, so [timeAt] keeps a back gesture's page under
 * the finger.
 */
object ScreenTransitions {
    /**
     * From one screen to a [deeper] one, which slides in over it; or back to a shallower one,
     * which comes back from under the screen sliding off (that one stays in front).
     */
    fun between(deeper: Boolean): ContentTransform =
        if (deeper) {
            enterOnTop togetherWith exitUnder
        } else {
            (enterUnder togetherWith exitOnTop).apply { targetContentZIndex = -1f }
        }

    /** The new screen, coming in over the old one from the end edge. */
    private val enterOnTop: EnterTransition = slideInHorizontally(PaneSlide) { it }

    /** The old screen, moving away under the new one. */
    private val exitUnder: ExitTransition =
        slideOutHorizontally(PaneSlide) { -it / 2 } + scaleOut(PaneFade, targetScale = UNDER_SCALE) + fadeOut(PaneFade)

    /** Going back: the previous screen comes back from under the current one. */
    val enterUnder: EnterTransition =
        slideInHorizontally(PaneSlide) { -it / 2 } + scaleIn(PaneFade, initialScale = UNDER_SCALE) + fadeIn(PaneFade)

    /** Going back: the current screen slides off towards the end edge. */
    val exitOnTop: ExitTransition = slideOutHorizontally(PaneSlide) { it }

    private const val UNDER_SCALE = 0.7f
}

/**
 * Follows a predictive back gesture's [events]: [seek]s the back transition along with the finger,
 * then calls [onBack] once the gesture completes. A cancelled gesture eases the preview back to
 * none and then runs [onCancel].
 */
suspend fun followBackGesture(
    events: Flow<BackEventCompat>,
    seek: suspend (Float) -> Unit,
    onBack: suspend () -> Unit,
    onCancel: suspend () -> Unit = {},
) {
    var fraction = 0f
    try {
        events.collect { event ->
            // Seeking runs the slide at that point of its time; undoing the slide's easing keeps
            // the page under the finger.
            fraction = timeAt(event.progress)
            seek(fraction)
        }
        onBack()
    } catch (e: CancellationException) {
        withContext(NonCancellable) {
            settleBack(fraction, seek)
            onCancel()
        }
        throw e
    }
}

/**
 * [followBackGesture] for a [ScreenTransitions] transition: previews going back from [from] to
 * [to], and returns to [from] when the gesture is cancelled.
 */
suspend fun <S> SeekableTransitionState<S>.followBack(events: Flow<BackEventCompat>, from: S, to: S, onBack: suspend () -> Unit) =
    followBackGesture(events, seek = { seekTo(it, targetState = to) }, onBack = onBack, onCancel = { snapTo(from) })

/** Eases a cancelled back gesture's preview from [fraction] back to none, [seek]ing every frame. */
private suspend fun settleBack(fraction: Float, seek: suspend (Float) -> Unit) {
    val start = withFrameNanos { it }
    while (true) {
        val t = (withFrameNanos { it } - start) / (SETTLE_MS * 1_000_000f)
        if (t >= 1f) break
        seek(fraction * (1f - FastOutSlowInEasing.transform(t)))
    }
    seek(0f)
}

private const val SETTLE_MS = 250

/**
 * The point in time at which a [PaneSlide] has covered [progress] of its way (the easing's
 * inverse). Seeking a slide to this keeps what's under the finger with the finger.
 */
private fun timeAt(progress: Float): Float {
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2
        if (PaneSlideEasing.transform(mid) < progress) low = mid else high = mid
    }
    return (low + high) / 2
}

/**
 * How panes move to new bounds. The library's default spring overshoots, so a pane folding shut
 * briefly got a negative width, and measuring that crashes ("width and height must be >= 0").
 */
private val PaneBounds = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntRect(1, 1, 1, 1),
)
