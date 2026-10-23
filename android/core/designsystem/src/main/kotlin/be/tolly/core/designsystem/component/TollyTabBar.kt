package be.tolly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import be.tolly.core.designsystem.theme.GlassSpec
import be.tolly.core.designsystem.theme.LocalGlassSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val BarHeight = 64.dp
private val BarPadding = 4.dp
private val LensHeight = BarHeight - BarPadding * 2
private val BarMarginHorizontal = 20.dp
private val BarMarginVertical = 8.dp

/** How much the lens grows while held: from the bar's inside height to well past its edges. */
private const val LENS_PRESSED_SCALE = 78f / 56f

/** How much the tabs seen through the held lens are magnified. */
private const val LENS_MAGNIFICATION = 1.2f

/**
 * How far a tab's icon and label stay from the tab's sides: the lens is a capsule as wide as a
 * tab, and its round ends cut into the corners where the label sits.
 */
private val TabContentInset = 7.dp

/** What scrolling content leaves free at the bottom for [TollyTabBar] (the bar plus its margins). */
val TollyTabBarSpace = BarHeight + BarMarginVertical * 2

/**
 * The glass look's tab bar, after iOS 26: a floating capsule of Liquid Glass with a lens on the
 * selected tab. Touching the bar grows the lens into a magnifying drop of glass under the finger;
 * slide along the bar and it follows, release and it settles on the nearest tab, which is then
 * selected. Tapping a tab sends the lens there the same way.
 *
 * [item] draws one tab (an icon over a label); it is drawn twice, once in the bar and once, in
 * the accent colour, for what the lens shows. [badge] goes on top of both, at the icon's corner.
 *
 * The badge is drawn apart from its tab, so TalkBack can't merge it in by itself: [badgeDescription]
 * gives the spoken form of a tab's badge (`3 unread announcements`), which is added to that tab's
 * own description. A tab without one has its badge read out as a node of its own.
 */
@Composable
fun TollyTabBar(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (index: Int) -> Unit = {},
    badgeDescription: @Composable (index: Int) -> String? = { null },
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val spec = LocalGlassSpec.current ?: return
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lens = remember(scope) { TabLens(scope, selectedIndex.coerceAtLeast(0).toFloat()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val tabsBackdrop = rememberLayerBackdrop()
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // Selection that comes from elsewhere (back navigation, a notification) moves the lens too.
    LaunchedEffect(lens, selectedIndex, itemCount) {
        if (selectedIndex >= 0 && itemCount > 0 && !lens.held) lens.moveTo(selectedIndex.coerceAtMost(itemCount - 1))
    }
    // Nothing selected (a screen outside the tabs): the lens has no tab to sit on, so it fades out.
    val lensAlpha by animateFloatAsState(if (selectedIndex >= 0 || lens.held) 1f else 0f, label = "lens alpha")

    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = BarMarginHorizontal, vertical = BarMarginVertical)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(BarHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val paddingPx = with(density) { BarPadding.toPx() }
        val tabWidthPx = (constraints.maxWidth - paddingPx * 2) / itemCount.coerceAtLeast(1)
        val tabWidth = with(density) { tabWidthPx.toDp() }
        val lastIndex = (itemCount - 1).coerceAtLeast(0)
        // The bar leans a little toward a drag, like something liquid being pulled.
        val lean = { lens.drag.let { drag -> with(density) { 6.dp.toPx() } * (drag / constraints.maxWidth).coerceIn(-1f, 1f) } }

        fun tabAt(x: Float): Float {
            val fromStart = if (ltr) x else constraints.maxWidth - x
            return ((fromStart - paddingPx) / tabWidthPx - 0.5f).coerceIn(0f, lastIndex.toFloat())
        }

        val gesture = Modifier.pointerInput(itemCount, tabWidthPx, ltr) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                // Ticks and the final choice follow the finger, not the lens, which lags behind it.
                var finger = tabAt(down.position.x)
                var tick = finger.roundToInt()
                var chosen: Int? = null
                lens.hold(finger)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        finger = tabAt(change.position.x)
                        lens.dragTo(finger, change.position.x - down.position.x)
                        val now = finger.roundToInt()
                        if (now != tick) {
                            tick = now
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                    chosen = finger.roundToInt().coerceIn(0, lastIndex)
                } finally {
                    // Also when the gesture is cancelled (rotation, a fold): the lens must not stay
                    // held, so without a choice it goes back to the selected tab.
                    lens.release(chosen ?: currentSelected.coerceIn(0, lastIndex))
                }
                chosen?.let { currentOnSelect(it) }
            }
        }

        val tabs: @Composable RowScope.(Boolean) -> Unit = { lensCopy ->
            repeat(itemCount) { index ->
                val selected = index == selectedIndex
                val description = if (lensCopy) null else badgeDescription(index)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (lensCopy) {
                                Modifier
                            } else {
                                Modifier.semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = selected
                                    onClick {
                                        currentOnSelect(index)
                                        true
                                    }
                                }
                            },
                        ),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = TabContentInset)
                            .graphicsLayer {
                                if (lensCopy) {
                                    val scale = lerp(1f, LENS_MAGNIFICATION, lens.press)
                                    scaleX = scale
                                    scaleY = scale
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.labelSmall) { item(index, selected) }
                    }
                    // Merged into the tab's description, so TalkBack reads the badge with the tab.
                    if (description != null) Spacer(Modifier.semantics { contentDescription = description })
                }
            }
        }

        // The bar.
        Row(
            Modifier
                .graphicsLayer { translationX = lean() }
                .glassBar(spec, lens, decorated = true)
                .then(gesture)
                .height(BarHeight)
                .fillMaxWidth()
                .padding(BarPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides onSurface) { tabs(false) }
        }

        // The same tabs in the accent colour, invisible themselves: the lens shows them, so the
        // selected tab takes the accent colour exactly where the lens covers it.
        // It matches the bar exactly, so the bar's edge seen through the lens is where the bar's edge is.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = lean() }
                .glassBar(spec, lens, decorated = false)
                .height(BarHeight)
                .fillMaxWidth()
                .padding(BarPadding)
                .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides accent) { tabs(true) }
        }

        // The lens: a soft grey pill at rest, a clear magnifying drop while held.
        Box(
            Modifier
                .padding(horizontal = BarPadding)
                .graphicsLayer {
                    alpha = lensAlpha
                    val offset = lens.position * tabWidthPx
                    translationX = (if (ltr) offset else -offset) + lean()
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(spec.backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        lens(10.dp.toPx() * lens.press, 14.dp.toPx() * lens.press, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = lens.press) },
                    shadow = { Shadow(alpha = lens.press) },
                    innerShadow = { InnerShadow(radius = 8.dp * lens.press, alpha = lens.press) },
                    layerBlock = {
                        var scaleX = lerp(1f, LENS_PRESSED_SCALE, lens.press)
                        var scaleY = scaleX
                        // A fast lens stretches along its path and thins out.
                        val speed = abs(lens.velocity) / itemCount.coerceAtLeast(1)
                        scaleX *= 1f + (speed * 0.08f).coerceAtMost(0.25f)
                        scaleY *= 1f - (speed * 0.03f).coerceAtMost(0.12f)
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                    },
                    onDrawSurface = {
                        val rest = 1f - lens.press
                        drawRect(if (spec.dark) Color.White.copy(alpha = 0.12f * rest) else Color.Black.copy(alpha = 0.07f * rest))
                        drawRect(Color.Black.copy(alpha = 0.03f * lens.press))
                    },
                )
                .height(LensHeight)
                .width(tabWidth),
        )

        // Badges sit on top of the glass, so the lens never tints them. A badge that its tab
        // describes (badgeDescription) is hidden from TalkBack here, or it would be read twice.
        Row(
            Modifier
                .graphicsLayer { translationX = lean() }
                .height(BarHeight)
                .fillMaxWidth()
                .padding(BarPadding),
        ) {
            repeat(itemCount) { index ->
                val described = badgeDescription(index) != null
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (described) Modifier.clearAndSetSemantics {} else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.offset(x = 12.dp, y = (-14).dp)) { badge(index) }
                }
            }
        }
    }
}

/**
 * A tab's label in [TollyTabBar]: on one line, made smaller when it is too long for the lens
 * (`Downloads`, `Instellingen` on a narrow phone) rather than cut off or sticking out of it.
 */
@Composable
fun TollyTabLabel(text: String) {
    val style = LocalTextStyle.current
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        style = style,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/**
 * The bar's own glass: blurred, refracted at its rim, and swelling a little while the lens is
 * held. Only the visible bar ([decorated]) gets a lit rim and a shadow.
 */
private fun Modifier.glassBar(spec: GlassSpec, lens: TabLens, decorated: Boolean): Modifier =
    drawBackdrop(
        backdrop = spec.backdrop,
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), 24.dp.toPx())
        },
        highlight = if (decorated) {
            { Highlight.Default.copy(alpha = if (spec.dark) 0.5f else 0.9f) }
        } else {
            null
        },
        shadow = if (decorated) {
            { Shadow(radius = 20.dp, color = Color.Black.copy(alpha = if (spec.dark) 0.35f else 0.12f)) }
        } else {
            null
        },
        layerBlock = {
            val scale = lerp(1f, 1f + 12.dp.toPx() / size.width, lens.press)
            scaleX = scale
            scaleY = scale
        },
        onDrawSurface = { drawRect(spec.chromeTint) },
    )

/**
 * Where the tab bar's lens is (in tabs, 0 is the first) and how far it is pressed (0 at rest,
 * 1 held). Springs move it, so it lags and settles like a drop of liquid.
 */
@Stable
private class TabLens(private val scope: CoroutineScope, initial: Float) {
    private val positionAnimation = Animatable(initial, 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val tracker = VelocityTracker()
    private val dragAnimation = Animatable(0f, 0.5f)

    val position: Float get() = positionAnimation.value
    val target: Float get() = positionAnimation.targetValue
    val press: Float get() = pressAnimation.value

    /** How far the finger is from where it went down, in pixels; springs back on release. */
    val drag: Float get() = dragAnimation.value

    /** In tabs per second. */
    var velocity by mutableFloatStateOf(0f)
        private set

    var held by mutableStateOf(false)
        private set

    fun hold(at: Float) {
        held = true
        tracker.resetTracking()
        scope.launch { pressAnimation.animateTo(1f, PressSpring) }
        follow(at)
    }

    fun dragTo(at: Float, dragged: Float) {
        scope.launch { dragAnimation.snapTo(dragged) }
        follow(at)
    }

    fun release(index: Int) {
        held = false
        scope.launch { dragAnimation.animateTo(0f, LeanSpring) }
        follow(index.toFloat())
        settle()
    }

    /** Moves to [index] as if tapped: the lens swells on the way and settles there. */
    fun moveTo(index: Int) {
        // Already there, or on the way (after a drag the lens settles by itself).
        if (abs(target - index) < 0.01f) return
        scope.launch { pressAnimation.animateTo(1f, PressSpring) }
        follow(index.toFloat())
        settle()
    }

    private fun follow(at: Float) {
        scope.launch {
            positionAnimation.animateTo(at, MoveSpring) {
                tracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
                this@TabLens.velocity = tracker.calculateVelocity().x
            }
            velocity = 0f
        }
    }

    /** Lets go of the press once the lens is nearly where it is going. */
    private fun settle() {
        scope.launch {
            snapshotFlow { abs(positionAnimation.value - positionAnimation.targetValue) < 0.05f || held }.first { it }
            if (!held) pressAnimation.animateTo(0f, PressSpring)
        }
    }

    private companion object {
        val PressSpring = spring<Float>(dampingRatio = 1f, stiffness = 800f, visibilityThreshold = 0.001f)
        val MoveSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 650f, visibilityThreshold = 0.001f)
        val LeanSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.5f)
    }
}
