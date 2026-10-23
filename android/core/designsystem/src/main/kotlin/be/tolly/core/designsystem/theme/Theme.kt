package be.tolly.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import be.tolly.core.model.ThemeMode
import be.tolly.core.model.UserPreferences

// Fallback palette from the logo's indigo (seed #4B45D6), used when dynamic color is off or
// unavailable: primary from Material's Fidelity scheme, so buttons stay as
// saturated as the icon, and the softer containers and surfaces from Tonal Spot.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3126BE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2DFFF),
    onPrimaryContainer = Color(0xFF414178),
    secondary = Color(0xFF5D5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E0F9),
    onSecondaryContainer = Color(0xFF464559),
    tertiary = Color(0xFF7A5368),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EB),
    onTertiaryContainer = Color(0xFF603C50),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),
    inversePrimary = Color(0xFFC2C1FF),
    surfaceDim = Color(0xFFDCD9E0),
    surfaceBright = Color(0xFFFCF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE5E1E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF1B00A6),
    primaryContainer = Color(0xFF414178),
    onPrimaryContainer = Color(0xFFE2DFFF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF464559),
    onSecondaryContainer = Color(0xFFE3E0F9),
    tertiary = Color(0xFFEAB9D2),
    onTertiary = Color(0xFF472639),
    tertiaryContainer = Color(0xFF603C50),
    onTertiaryContainer = Color(0xFFFFD8EB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = Color(0xFF4E48D9),
    surfaceDim = Color(0xFF131318),
    surfaceBright = Color(0xFF39383F),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
)

// The glass look follows iOS: grouped grey (black in dark) screens, white (dark grey) grouped
// rows, a blue tint and grey secondary labels.
private val IosBlue = Color(0xFF0088FF)
private val IosBlueDark = Color(0xFF0091FF)

private val GlassLightColors = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEFF),
    onPrimaryContainer = Color(0xFF0060B8),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFFAF52DE),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E1FA),
    onTertiaryContainer = Color(0xFF6E2C91),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2E0),
    onErrorContainer = Color(0xFFB0241B),
    background = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    inversePrimary = IosBlueDark,
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE5E5EA),
)

private val GlassDarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF002E52),
    onPrimaryContainer = Color(0xFF6CB8FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBF5AF2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3A1E4A),
    onTertiaryContainer = Color(0xFFE2B8FA),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF4D1512),
    onErrorContainer = Color(0xFFFFB4AE),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    inversePrimary = IosBlue,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color(0xFF0E0E0F),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
)

/** Material 3 Expressive's rounder shape scale. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * iOS's text styles on Android's font: bold large titles, 17 sp body text with tighter tracking,
 * 15 sp secondary text and small grey section labels.
 */
private val GlassTypography = Typography().run {
    copy(
        // The large title of a screen (TollyTopBar).
        displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        // A collapsed or inline screen title.
        titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
}

/**
 * The colours the design system adds to the Material scheme: what a screen is painted with, and
 * the grouped rows and cards on it. Both looks put lighter (or darker, in dark mode) grouped rows
 * on a tinted screen, like Android 16's and iOS's settings.
 */
@Immutable
class TollyColors(
    /** Behind everything: the screen, its top bar and (Material) navigation bar. */
    val screen: Color,
    /** Grouped list rows and cards. */
    val group: Color,
    /** A row that is open in the pane next to the list. */
    val groupSelected: Color,
    /** Hairlines between grouped rows; transparent where rows are separated by a gap instead. */
    val separator: Color,
)

val LocalTollyColors = staticCompositionLocalOf {
    TollyColors(screen = Color.Unspecified, group = Color.Unspecified, groupSelected = Color.Unspecified, separator = Color.Transparent)
}

/** Shortcut for the design system's extra colours. */
object Tolly {
    val colors: TollyColors
        @Composable @ReadOnlyComposable get() = LocalTollyColors.current

    /** True with the glass look: iOS styling and Liquid Glass bars. */
    val glass: Boolean
        @Composable @ReadOnlyComposable get() = LocalGlass.current
}

val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The dark scheme on black: screens become pure black and the surfaces on them (grouped rows,
 * cards, bars) are darkened towards black, keeping their tint so they still stand apart.
 */
private fun ColorScheme.amoled(): ColorScheme {
    fun Color.darker(fraction: Float) = lerp(this, Color.Black, fraction)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLow.darker(0.55f),
        surfaceContainer = surfaceContainer.darker(0.45f),
        surfaceContainerHigh = surfaceContainerHigh.darker(0.35f),
        surfaceContainerHighest = surfaceContainerHighest.darker(0.25f),
        surfaceBright = surfaceBright.darker(0.3f),
        surfaceVariant = surfaceVariant.darker(0.3f),
        secondaryContainer = secondaryContainer.darker(0.2f),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TollyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** The glass look: iOS styling with Liquid Glass bars. It has its own palette, so [dynamicColor] doesn't apply. */
    glass: Boolean = false,
    /** Pure black screens in dark mode, for OLED displays; has no effect in light mode. */
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base: ColorScheme = when {
        glass -> if (darkTheme) GlassDarkColors else GlassLightColors
        dynamicColor && supportsDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colors = remember(base, darkTheme, amoled) { if (darkTheme && amoled) base.amoled() else base }
    val tolly = remember(colors, darkTheme, glass) {
        if (glass) {
            TollyColors(
                screen = colors.background,
                group = colors.surfaceContainer,
                groupSelected = if (darkTheme) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                separator = if (darkTheme) Color(0xFF38383A) else Color(0xFFC6C6C8),
            )
        } else {
            TollyColors(
                screen = if (darkTheme) colors.surface else colors.surfaceContainer,
                group = if (darkTheme) colors.surfaceContainer else colors.surfaceBright,
                groupSelected = colors.secondaryContainer,
                separator = Color.Transparent,
            )
        }
    }
    // One call site for the content whatever the look, so switching looks keeps the app's state
    // (the open tab, scroll positions).
    val backdrop = rememberLayerBackdrop()
    val spec = remember(glass, darkTheme, backdrop) { if (glass) GlassSpec(dark = darkTheme, backdrop = backdrop) else null }
    CompositionLocalProvider(LocalGlass provides glass, LocalGlassSpec provides spec, LocalTollyColors provides tolly) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (glass) MotionScheme.standard() else MotionScheme.expressive(),
            shapes = if (glass) GlassShapes else ExpressiveShapes,
            typography = if (glass) GlassTypography else Typography(),
            content = content,
        )
    }
}

/** [TollyTheme] with the user's choices from Settings: theme, colours and look. */
@Composable
fun TollyTheme(preferences: UserPreferences, content: @Composable () -> Unit) {
    TollyTheme(
        darkTheme = preferences.themeMode.isDark(),
        dynamicColor = preferences.dynamicColor,
        glass = preferences.glass,
        amoled = preferences.amoled,
        content = content,
    )
}
