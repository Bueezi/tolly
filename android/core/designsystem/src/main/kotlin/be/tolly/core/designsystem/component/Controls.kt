package be.tolly.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Label
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.theme.LocalChromeBackdrop
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.glassChrome
import be.tolly.core.designsystem.theme.glassSpec
import kotlin.math.roundToInt

/**
 * A switch: Material 3 Expressive's, with a check in the thumb when on, or iOS 26's wide pill
 * with the glass look. Pass a null [onCheckedChange] when the whole row toggles. A switch that
 * toggles by itself has a touch area of at least 48 dp, so it is that tall in a row.
 */
@Composable
fun TollySwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!Tolly.glass) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
        return
    }
    val dark = glassSpec?.dark == true
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "switch")
    // The thumb stretches while held, like iOS's.
    val thumbWidth by animateDpAsState(if (pressed) 44.dp else 38.dp, label = "thumb")
    val off = if (dark) Color(0xFF39393D) else Color(0xFFE9E9EB)
    val on = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    Box(
        modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            // The pill is 28 dp high, but a finger needs 48: the touch area grows around it.
            .then(if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(width = 62.dp, height = 28.dp)
            .background(lerp(off, on, progress), Capsule()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = 2.dp + (62.dp - 4.dp - thumbWidth) * progress)
                .size(width = thumbWidth, height = 24.dp)
                .shadow(2.dp, Capsule())
                .background(Color.White, Capsule()),
        )
    }
}

/**
 * A choice of one of [count] options: Material 3 Expressive's connected button group, or iOS's
 * segmented control with a sliding thumb with the glass look. Both are read as a group of radio
 * buttons. The glass look draws a 36 dp track but is 48 dp high, so every segment is easy to hit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TollySegmented(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Where the glass look's thumb is, in segments (1.5 halfway between the second and third):
     * a pager passes where it is, so the thumb follows a swipe. Without it the thumb springs to
     * [selectedIndex].
     */
    position: (() -> Float)? = null,
    label: @Composable (index: Int) -> Unit,
) {
    // The labels agree on one text size (see [SegmentLabel]).
    val textSizes = remember { SegmentTextSizes() }
    val sizedLabel: @Composable (Int) -> Unit = { index ->
        CompositionLocalProvider(LocalSegmentTextSizes provides textSizes, LocalSegmentIndex provides index) { label(index) }
    }
    if (!Tolly.glass) {
        Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            repeat(count) { index ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val shape = segmentShape(leading = index == 0, trailing = index == count - 1, checked = index == selectedIndex, pressed = pressed)
                ToggleButton(
                    checked = index == selectedIndex,
                    onCheckedChange = { onSelect(index) },
                    // One of n, read as a radio button rather than the toggle button's checkbox.
                    modifier = Modifier.weight(1f).semantics {
                        role = Role.RadioButton
                        selected = index == selectedIndex
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    shapes = remember(shape) { ToggleButtonShapes(shape, shape, shape) },
                    interactionSource = interaction,
                ) { sizedLabel(index) }
            }
        }
        return
    }
    val dark = glassSpec?.dark == true
    val track = if (dark) Color(0x3D767680) else Color(0x1F767680)
    val thumb = if (dark) Color(0xFF5A5A5E) else Color.White
    // The track is 36 dp high like iOS's, but the segments reach 48 dp: the touch area is taller
    // than what is drawn.
    BoxWithConstraints(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        val width = maxWidth / count.coerceAtLeast(1)
        val segmentPx = constraints.maxWidth / count.coerceAtLeast(1).toFloat()
        val offset by animateDpAsState(
            width * selectedIndex.coerceAtLeast(0),
            spring(dampingRatio = 0.75f, stiffness = 450f),
            label = "segment",
        )
        Box(Modifier.fillMaxWidth().height(36.dp).clip(Capsule()).background(track)) {
            // Nothing chosen yet (an index of -1): no thumb.
            if (selectedIndex >= 0 || position != null) Box(
                Modifier
                    .then(
                        if (position != null) {
                            Modifier.offset { IntOffset((position().coerceIn(0f, (count - 1).toFloat()) * segmentPx).roundToInt(), 0) }
                        } else {
                            Modifier.offset(x = offset)
                        },
                    )
                    .width(width)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .shadow(3.dp, Capsule(), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .background(thumb, Capsule()),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            repeat(count) { index ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.RadioButton,
                            interactionSource = null,
                            indication = null,
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { sizedLabel(index) }
                    }
                }
            }
        }
    }
}

/**
 * The corners of one button of a connected group, as Material's: round at the group's ends and
 * when checked, tight between buttons and tighter while pressed. Material morphs these itself, but
 * its bouncy spring can carry a corner a fraction of a pixel below zero, which crashes (it did on
 * a tablet). This shape bounces the same way but keeps every corner between 0 and round.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun segmentShape(leading: Boolean, trailing: Boolean, checked: Boolean, pressed: Boolean): Shape {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val start = animateFloatAsState(if (checked || leading) 1f else 0f, spec, label = "start corners")
    val end = animateFloatAsState(if (checked || trailing) 1f else 0f, spec, label = "end corners")
    val inner = animateFloatAsState(if (pressed) SegmentInnerPressed.value else SegmentInner.value, spec, label = "inner corners")
    return remember { SegmentShape({ start.value }, { end.value }, { inner.value.dp }) }
}

/** Material's inner corners of a connected button group (small, and extra small while pressed). */
private val SegmentInner = 8.dp
private val SegmentInnerPressed = 4.dp

/** Corners [start] and [end] go from [inner] (0) to round (1); read while drawing, so they animate without recomposing. */
private class SegmentShape(val start: () -> Float, val end: () -> Float, val inner: () -> Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val round = size.minDimension / 2
        val innerPx = with(density) { inner().toPx() }.coerceIn(0f, round)
        fun corner(fraction: Float) = CornerRadius((innerPx + (round - innerPx) * fraction).coerceIn(0f, round))
        val startCorner = corner(start())
        val endCorner = corner(end())
        val (left, right) = if (layoutDirection == LayoutDirection.Ltr) startCorner to endCorner else endCorner to startCorner
        return Outline.Rounded(RoundRect(size.toRect(), topLeft = left, topRight = right, bottomRight = right, bottomLeft = left))
    }
}

/**
 * A [TollySegmented] option's label: one line that shrinks its text to fit its segment, so a
 * long word (Nederlands, Announcements) isn't cut off on a narrow phone. All labels of one
 * control take the size the longest one needs, so they don't sit side by side in different sizes.
 *
 * A [badge] (an unread count) hangs off the top end of the text like a superscript instead of
 * sitting beside it, so it takes no width from the text, which would otherwise be cut off (three
 * segments on a phone leave `Announcements` little room to begin with).
 */
@Composable
fun SegmentLabel(text: String, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null) {
    val style = LocalTextStyle.current
    val color = LocalContentColor.current
    val largest = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val shared = LocalSegmentTextSizes.current
    val index = LocalSegmentIndex.current
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val fits = remember(text, style, largest, maxWidth) {
            var size = largest.value
            if (constraints.hasBoundedWidth) {
                while (size > MIN_SEGMENT_TEXT && measurer.measure(text, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > maxWidth) {
                    size -= 0.5f
                }
            }
            size
        }
        if (shared != null) {
            SideEffect { shared.sizes[index] = fits }
            DisposableEffect(shared, index) { onDispose { shared.sizes.remove(index) } }
        }
        val size = minOf(fits, shared?.smallest ?: fits)
        Box {
            BasicText(
                text,
                style = style.copy(color = color, textAlign = TextAlign.Center, fontSize = size.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-9).dp)) { badge() }
            }
        }
    }
}

/** The text size (sp) at which each label of one [TollySegmented] fits; they all use the smallest. */
@Stable
private class SegmentTextSizes {
    val sizes = mutableStateMapOf<Int, Float>()
    val smallest: Float? get() = sizes.values.minOrNull()
}

private val LocalSegmentTextSizes = compositionLocalOf<SegmentTextSizes?> { null }
private val LocalSegmentIndex = compositionLocalOf { 0 }
private const val MIN_SEGMENT_TEXT = 9f

/**
 * A choice of one of [count] stops along a line, for values with a natural order (how often,
 * how much): Material 3 Expressive's slider with stop dots, or iOS's thin slider with a white
 * knob with the glass look. [onValueChange] follows the finger, with a tick at each stop;
 * [onValueChangeFinished] comes once the finger lets go, the moment to save the choice. With
 * [valueLabel], Material shows the value in a bubble above the knob while it's dragged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TollySlider(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabel: (@Composable (Int) -> String)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val last = (count - 1).coerceAtLeast(1)
    // Read out as the slider's state (`Weekly`) rather than a bare position.
    val description = valueLabel?.invoke(value)
    val spoken = if (description != null) modifier.semantics { stateDescription = description } else modifier
    val onChange: (Float) -> Unit = { raw ->
        val index = raw.roundToInt().coerceIn(0, last)
        if (index != value) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            onValueChange(index)
        }
    }
    val state = remember(count) { SliderState(value.toFloat(), (count - 2).coerceAtLeast(0), 0f..last.toFloat()) }
    // A value set from outside (another screen, a reset) moves the knob too.
    SideEffect { if (state.value.roundToInt() != value) state.value = value.toFloat() }
    if (!Tolly.glass) {
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            state = state,
            onValueChange = { raw ->
                state.value = raw
                onChange(raw)
            },
            modifier = spoken,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            thumb = {
                if (valueLabel == null) {
                    SliderDefaults.Thumb(interactionSource = interactionSource, enabled = enabled)
                } else {
                    Label(
                        label = { PlainTooltip(modifier = Modifier.wrapContentWidth()) { Text(valueLabel(value)) } },
                        interactionSource = interactionSource,
                    ) {
                        SliderDefaults.Thumb(interactionSource = interactionSource, enabled = enabled)
                    }
                }
            },
        )
        return
    }
    val dark = glassSpec?.dark == true
    val fill = MaterialTheme.colorScheme.primary
    val track = if (dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    Slider(
        state = state,
        onValueChange = { raw ->
            state.value = raw
            onChange(raw)
        },
        modifier = spoken.alpha(if (enabled) 1f else 0.4f),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                    .background(Color.White, CircleShape),
            )
        },
        track = { sliderState ->
            val fraction = sliderState.coercedValueAsFraction
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                // Small marks at the stops, like iOS's ticked sliders.
                if (count in 3..12) {
                    for (stop in 1 until count - 1) {
                        val x = size.width * stop / (count - 1)
                        drawCircle(if (stop / (count - 1f) <= fraction) fill else track, radius = 3.dp.toPx(), center = Offset(x, size.height / 2))
                    }
                }
            }
        },
    )
}

/**
 * A top bar's back (or, with [close], close) button: a tonal circle in Material, a glass circle
 * with a chevron with the glass look.
 */
@Composable
fun TollyBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, close: Boolean = false) {
    val description = stringResource(if (close) R.string.action_close else R.string.action_back)
    if (!Tolly.glass) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.padding(start = 4.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Icon(if (close) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = description)
        }
        return
    }
    GlassCircleButton(onClick = onClick, modifier = modifier.padding(start = 8.dp)) {
        if (close) {
            Icon(Icons.Filled.Close, contentDescription = description, modifier = Modifier.size(22.dp))
        } else {
            // The chevron's own artwork sits right of centre.
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = description, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        }
    }
}

/** A round glass button floating over the content, like iOS 26's bar buttons. */
@Composable
fun GlassCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalChromeBackdrop.current
    val spec = glassSpec
    val interaction = remember { MutableInteractionSource() }
    // The button is drawn 44 dp wide, but its touch area is at least 48 dp.
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .then(
                    when {
                        backdrop != null -> Modifier.glassChrome(backdrop, CircleShape, refraction = 10.dp)
                        spec != null -> Modifier.background(spec.chromeTint, CircleShape)
                        else -> Modifier
                    },
                )
                .clip(CircleShape)
                .indication(interaction, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
        }
    }
}
