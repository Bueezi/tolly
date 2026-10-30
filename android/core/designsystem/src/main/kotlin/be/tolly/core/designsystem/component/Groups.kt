package be.tolly.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.kyant.shapes.UnevenRoundedRectangle
import be.tolly.core.designsystem.theme.LocalGlass
import be.tolly.core.designsystem.theme.LocalTollyColors
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.model.Highlight

/** Where a row sits in its group, which decides its corners (and, with the glass look, its separator). */
@Immutable
data class ItemPosition(val index: Int, val count: Int) {
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == count - 1

    companion object {
        val Single = ItemPosition(0, 1)
    }
}

/** The geometry of grouped lists. */
object GroupDefaults {
    /** Between a group and the screen edge. */
    val Margin = 16.dp

    /** Material: the round outer corners of a group and the tight ones between its rows. */
    internal val OuterRadius = 22.dp
    internal val InnerRadius = 6.dp

    /** Material: the gap between rows. */
    internal val Gap = 2.dp

    /** The glass look: iOS's inset grouped corners. Rows are joined and separated by hairlines. */
    internal val GlassRadius = 24.dp

    /** Where a separator starts for rows with leading content of [leading] width. */
    fun separatorStart(leading: Dp = 0.dp): Dp = if (leading > 0.dp) 16.dp + leading + 16.dp else 16.dp
}

/** The shape of a row at [position] in its group. */
@Composable
fun groupShape(position: ItemPosition): Shape {
    val glass = Tolly.glass
    return remember(glass, position) {
        if (glass) {
            val top = if (position.isFirst) GroupDefaults.GlassRadius else 0.dp
            val bottom = if (position.isLast) GroupDefaults.GlassRadius else 0.dp
            UnevenRoundedRectangle(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
        } else {
            val top = if (position.isFirst) GroupDefaults.OuterRadius else GroupDefaults.InnerRadius
            val bottom = if (position.isLast) GroupDefaults.OuterRadius else GroupDefaults.InnerRadius
            RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
        }
    }
}

private const val PRESSED_SCALE = 0.97f

/**
 * Makes this a row of a grouped list: inset from the screen edge, on the group colour, with
 * corners that depend on its [position]. Material groups have a small gap between rows and the
 * rows squish a little when pressed; glass (iOS) groups join their rows with hairline separators
 * starting at [separatorStart].
 *
 * With [onClick] the row is clickable; long clicks give the system's long-press haptic.
 * A [highlight] colour tints the row and marks its leading edge (see [highlightColor]); a
 * different [tint] keeps the mark but tints the row in its own colour, for a row that is
 * highlighted and also stands out for another reason.
 */
fun Modifier.groupItem(
    position: ItemPosition,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    separatorStart: Dp = GroupDefaults.separatorStart(),
    highlight: Color = Color.Unspecified,
    onClickLabel: String? = null,
    tint: Color = highlight,
): Modifier = composed {
    // Inside [SwipeActions] the row's frame (margin, gap and corners) is already there.
    val framed = LocalInGroupFrame.current
    val glass = LocalGlass.current
    val colors = LocalTollyColors.current
    val shape = groupShape(position)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !glass) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press scale",
    )
    val separator = colors.separator
    val background = when {
        selected -> colors.groupSelected
        tint.isSpecified -> tint.copy(alpha = HighlightTint).compositeOver(colors.group)
        else -> colors.group
    }
    this
        .then(if (framed) Modifier else Modifier.groupFrame(position, glass))
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(background)
        .then(
            if (highlight.isSpecified) {
                Modifier.drawWithContent {
                    drawContent()
                    // A pill inset from the edges, so the group's rounded corners never cut into it.
                    val width = HighlightEdge.toPx()
                    val inset = HighlightInset.toPx()
                    val height = (size.height - 2 * HighlightInsetVertical.toPx()).coerceAtLeast(width)
                    val x = if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset - width
                    drawRoundRect(
                        highlight,
                        topLeft = Offset(x, (size.height - height) / 2),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(width / 2),
                    )
                }
            } else {
                Modifier
            },
        )
        .then(
            if (onClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = role,
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
        .then(
            if (glass && !position.isFirst && separator != Color.Transparent) {
                Modifier.drawWithContent {
                    drawContent()
                    val stroke = 0.5.dp.toPx().coerceAtLeast(1f)
                    val inset = separatorStart.toPx()
                    val (from, to) = if (layoutDirection == LayoutDirection.Ltr) inset to size.width else 0f to size.width - inset
                    drawLine(separator, Offset(from, stroke / 2), Offset(to, stroke / 2), strokeWidth = stroke)
                }
            } else {
                Modifier
            },
        )
}

/** How strongly a highlight colour tints its row, and the bar near its leading edge. */
private const val HighlightTint = 0.16f
private val HighlightEdge = 4.dp
private val HighlightInset = 6.dp
private val HighlightInsetVertical = 12.dp

/** True inside a [SwipeActions], which draws the row's frame itself. */
internal val LocalInGroupFrame = staticCompositionLocalOf { false }

/** A grouped row's place in its group: inset from the screen edge, with Material's gap below it. */
internal fun Modifier.groupFrame(position: ItemPosition, glass: Boolean): Modifier = padding(
    start = GroupDefaults.Margin,
    end = GroupDefaults.Margin,
    bottom = if (!glass && !position.isLast) GroupDefaults.Gap else 0.dp,
)

/** The colour of a row the user marked with [highlight]: iOS system colours, as for [TileColor]. */
fun highlightColor(highlight: Highlight): Color = when (highlight) {
    Highlight.ORANGE -> TileColor.Orange.ios
}

/**
 * A list row for [groupItem]: Material's list item on a transparent background, so it takes the
 * group's colour.
 */
@Composable
fun TollyListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overline: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        leadingContent = leading,
        trailingContent = trailing,
        overlineContent = overline,
        supportingContent = supporting,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        content = headline,
    )
}

/** A group's heading: a small coloured label (Material) or small grey capitals (glass, like iOS). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (Tolly.glass) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
            modifier = modifier.semantics { heading() }.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp),
        )
    } else {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color.takeOrElse { MaterialTheme.colorScheme.primary },
            modifier = modifier.semantics { heading() }.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 10.dp),
        )
    }
}

/** The colour of an [IconTile]: an iOS system colour with the glass look, a tonal role in Material. */
enum class TileColor(val ios: Color) {
    Blue(Color(0xFF0088FF)),
    Indigo(Color(0xFF5856D6)),
    Purple(Color(0xFFAF52DE)),
    Pink(Color(0xFFFF2D55)),
    Red(Color(0xFFFF3B30)),
    Orange(Color(0xFFFF9500)),
    Yellow(Color(0xFFFFB800)),
    Green(Color(0xFF34C759)),
    Teal(Color(0xFF00A6C7)),
    Gray(Color(0xFF8E8E93)),
}

/** The size an [IconTile] takes, for [GroupDefaults.separatorStart]. */
val IconTileSize: Dp
    @Composable get() = if (Tolly.glass) 30.dp else 40.dp

/**
 * The leading icon of a settings-style row: a tonal circle (Material, like RikkaHub) or a white
 * glyph on a coloured rounded square (glass, like iOS Settings).
 */
@Composable
fun IconTile(icon: ImageVector, color: TileColor, modifier: Modifier = Modifier) {
    if (Tolly.glass) {
        Box(
            modifier
                .size(30.dp)
                .background(color.ios, RoundedRectangle(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (color) {
        TileColor.Blue, TileColor.Indigo, TileColor.Teal -> scheme.primaryContainer to scheme.onPrimaryContainer
        TileColor.Purple, TileColor.Pink -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TileColor.Green, TileColor.Yellow, TileColor.Orange -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TileColor.Red -> scheme.errorContainer to scheme.onErrorContainer
        TileColor.Gray -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Box(modifier.size(40.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}
