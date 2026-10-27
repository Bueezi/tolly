package be.tolly.core.designsystem.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** True while the glass look is on. */
val LocalGlass = staticCompositionLocalOf { false }

/** How much glass this device can draw. */
enum class GlassTier {
    /** Below Android 12 there is no blur: glass is a dense frosted tint. */
    TINT,

    /** Android 12: the content behind glass is blurred. */
    BLUR,

    /** Android 13 and up: blur plus Liquid Glass refraction at the edges and a light rim. */
    LIQUID,
}

val deviceGlassTier: GlassTier
    get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassTier.LIQUID
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.BLUR
        else -> GlassTier.TINT
    }

/**
 * The glass look follows iOS 26: screens are plain grouped lists, and only the navigation layer
 * is glass. That layer floats over the content and refracts it: the tab bar, the buttons in the
 * top bars and the top bars' soft scroll edge.
 *
 * [backdrop] records the whole app under the tab bar (see [glassBackdropSource]); each
 * TollyScaffold records its own content for its top bar.
 */
@Stable
class GlassSpec internal constructor(
    val dark: Boolean,
    val backdrop: LayerBackdrop,
) {
    /** The milky fill of glass chrome; denser where the device can't blur what is behind it. */
    val chromeTint: Color = when {
        deviceGlassTier == GlassTier.TINT -> if (dark) Color(0xF01C1C1E) else Color(0xF2FFFFFF)
        dark -> Color(0x661C1C1E)
        else -> Color(0x99FFFFFF)
    }
}

val LocalGlassSpec = staticCompositionLocalOf<GlassSpec?> { null }

/** The backdrop glass chrome inside a screen's top bar refracts; set by TollyScaffold. */
val LocalChromeBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** The glass spec, or null without the glass look. */
val glassSpec: GlassSpec?
    @Composable @ReadOnlyComposable get() = LocalGlassSpec.current

/**
 * Records the app's content as what the floating tab bar refracts. Put it on everything but the
 * tab bar itself (a backdrop can't contain what draws it). Does nothing without the glass look,
 * nor while something on screen must stay out of the recording ([keepOutOfGlass]).
 */
fun Modifier.glassBackdropSource(): Modifier = composed {
    val spec = LocalGlassSpec.current ?: return@composed this
    if ((LocalUnrecordedContent.current?.count ?: 0) > 0) return@composed this
    layerBackdrop(spec.backdrop)
}

/** How many things on screen keep the tab bar's glass from recording the app ([keepOutOfGlass]). */
@Stable
class UnrecordedContent internal constructor() {
    internal var count by mutableIntStateOf(0)
}

/** Set by TollyTheme, so the count outlives switching looks and themes. */
val LocalUnrecordedContent = staticCompositionLocalOf<UnrecordedContent?> { null }

/**
 * For content that must never be drawn into the glass's offscreen recordings: a WebView. Drawn
 * there, its GPU drawing crashes the renderer on Vulkan, and drawn in software instead its text
 * comes out squashed. While this is composed the tab bar's glass doesn't record the app and
 * refracts the bare screen, as on the Mail tab; the screen's own top bar needs
 * `TollyScaffold(recordContent = false)`. Returns whether the content may be drawn yet: from the
 * frame after the recording stopped, so it is never in one.
 */
@Composable
fun keepOutOfGlass(): Boolean {
    val content = LocalUnrecordedContent.current ?: return true
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(content) {
        content.count++
        ready = true
        onDispose { content.count-- }
    }
    return ready
}

/**
 * Liquid Glass for a floating control, like a capsule of toolbar buttons: the content behind it
 * blurred, refracted at the edges and lit along the rim, over a milky tint.
 */
fun Modifier.glassChrome(backdrop: Backdrop, shape: Shape, refraction: Dp = 16.dp): Modifier = composed {
    val spec = LocalGlassSpec.current ?: return@composed this
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
            lens(refraction.toPx() * 0.75f, refraction.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = if (spec.dark) 0.6f else 1f) },
        shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = if (spec.dark) 0.3f else 0.1f)) },
        onDrawSurface = { drawRect(spec.chromeTint) },
    )
}

/**
 * iOS's scroll edge: where content scrolls under a top bar it is blurred and fades into the
 * screen colour, without a hard edge. [screen] is the colour it fades to.
 */
fun Modifier.glassScrollEdge(backdrop: Backdrop, screen: Color): Modifier =
    drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = { blur(10.dp.toPx()) },
        onDrawSurface = {
            drawRect(Brush.verticalGradient(0f to screen.copy(alpha = 0.85f), 1f to screen.copy(alpha = 0.35f)))
        },
        // Fade the whole effect out over its bottom third.
        onDrawFront = {
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        },
    )

/**
 * Room to leave at the bottom of scrolling content for the floating glass tab bar, which content
 * scrolls under. Zero when there is no floating bar.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }
