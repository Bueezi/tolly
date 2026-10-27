package be.tolly.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.designsystem.theme.LocalChromeBackdrop
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.glassChrome
import be.tolly.core.designsystem.theme.glassScrollEdge
import be.tolly.core.designsystem.theme.glassSpec
import kotlinx.coroutines.delay

/** The scroll behavior of the nearest [TollyScaffold], which its [TollyTopBar] follows. */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

/**
 * A screen: a [TollyTopBar] over the content, on the screen colour. Lists that pass [content]'s
 * padding as their content padding (see [scrollingContentPadding]) scroll under the top bar and
 * the floating tab bar instead of stopping at them; with the glass look the bars refract them.
 *
 * Without a [scrollBehavior] the bar darkens or blurs once content scrolls under it. Snackbars
 * of [snackbarHostState] show above the floating tab bar.
 *
 * Content that must not be drawn into an offscreen layer, like a WebView (drawn twice it runs out
 * of tile memory and flickers), passes [recordContent] = false: the top bar's glass then refracts
 * the bare screen, which is all there is under it when nothing scrolls beneath the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TollyScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    snackbarHostState: SnackbarHostState? = null,
    recordContent: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val behavior = scrollBehavior ?: TopAppBarDefaults.pinnedScrollBehavior()
    val screen = Tolly.colors.screen
    // With the glass look the content is recorded, for the top bar's glass to refract.
    val backdrop = if (glassSpec != null) rememberLayerBackdrop() else null
    CompositionLocalProvider(LocalTopBarScrollBehavior provides behavior, LocalChromeBackdrop provides backdrop) {
        Scaffold(
            modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
            topBar = topBar,
            snackbarHost = {
                if (snackbarHostState != null) SnackbarHost(snackbarHostState, Modifier.padding(bottom = LocalBottomBarInset.current))
            },
            containerColor = screen,
        ) { padding ->
            when {
                backdrop == null -> Box(Modifier.fillMaxSize()) { content(padding) }
                recordContent -> Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(screen)) { content(padding) }
                else -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(screen))
                    content(padding)
                }
            }
        }
    }
}

/**
 * A screen's top bar, with its title in the bar: big titles above the content cost a phone a fifth
 * of its height, and nothing in Tolly needs them. A tab's own bar shows its title at the start, a
 * little larger; a [detail] pane's bar shows the item's title (centred with the glass look, as iOS
 * does for a pushed screen).
 *
 * With the glass look the bar itself is clear: content scrolling under it is blurred and fades
 * out (iOS's scroll edge), and [navigationIcon] and [actions] sit in glass. [bottom] holds what
 * belongs to the bar but sits under the title, like a search field or a segmented control.
 * While [refreshing], a [RefreshBar] runs along the bar's bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TollyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    detail: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = LocalTopBarScrollBehavior.current,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    val glass = Tolly.glass
    val screen = Tolly.colors.screen
    val backdrop = LocalChromeBackdrop.current
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = if (glass) Color.Transparent else screen,
        scrolledContainerColor = if (glass) Color.Transparent else screen,
    )
    val barActions: @Composable RowScope.() -> Unit = if (glass && backdrop != null) {
        {
            // iOS 26 groups a bar's buttons in one capsule of glass. Without buttons the row has
            // no height, so no glass is drawn.
            Row(
                Modifier.padding(end = 8.dp).glassChrome(backdrop, Capsule(), refraction = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    } else {
        actions
    }
    val barTitle: @Composable () -> Unit = {
        if (detail) title() else ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
    }
    val bar: @Composable ColumnScope.() -> Unit = {
        when {
            glass && detail -> CenterAlignedTopAppBar(
                title = {
                    if (subtitle == null) {
                        title()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            title()
                            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { subtitle() }
                            }
                        }
                    }
                },
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
            subtitle != null -> TopAppBar(
                title = barTitle,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
            else -> TopAppBar(
                title = barTitle,
                navigationIcon = navigationIcon,
                actions = barActions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }
        bottom?.invoke(this)
        RefreshBar(refreshing)
    }
    if (!glass || backdrop == null) {
        Column(modifier.background(if (glass) Color.Transparent else screen), content = bar)
        return
    }
    // The scroll edge only shows once something is under the bar; read in the layer, not in composition.
    val scrolled by remember(scrollBehavior) {
        derivedStateOf { (scrollBehavior?.state?.contentOffset ?: 0f) < -1f }
    }
    val edgeAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "scrollEdge")
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .extendBottom(ScrollEdgeOverhang)
                .graphicsLayer { alpha = edgeAlpha }
                .glassScrollEdge(backdrop, screen),
        )
        Column(content = bar)
    }
}

/** How far the scroll edge's fade reaches below the bar. */
private val ScrollEdgeOverhang = 20.dp

/** Lays this out [extra] taller than it is given, overflowing at the bottom. */
private fun Modifier.extendBottom(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minHeight = constraints.minHeight + extraPx, maxHeight = constraints.maxHeight + extraPx),
    )
    layout(placeable.width, (placeable.height - extraPx).coerceAtLeast(0)) { placeable.place(0, 0) }
}

/**
 * Content padding for a list that scrolls under the bars: [TollyScaffold]'s [padding] plus room
 * for the floating tab bar, with [top] and [bottom] extra.
 */
@Composable
fun scrollingContentPadding(padding: PaddingValues, top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding() + top,
        end = padding.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() + bottom + LocalBottomBarInset.current,
    )
}

/**
 * Shows that something is being refreshed: a bar under the top bar that fills like ObtainX's. It
 * rushes to a third as [refreshing] starts, creeps on (never quite arriving) while it lasts, and
 * runs to the end once it's done, then slides away. Material 3 Expressive's wavy progress
 * indicator; a thin straight line with the glass look.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RefreshBar(refreshing: Boolean, modifier: Modifier = Modifier) {
    val glass = Tolly.glass
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            shown = true
            progress.snapTo(0f)
            progress.animateTo(REFRESH_FIRST_STEP, tween(500, easing = FastOutSlowInEasing))
            progress.animateTo(REFRESH_CREEP_TO, tween(REFRESH_CREEP_MS, easing = LinearOutSlowInEasing))
        } else if (shown) {
            progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            delay(200)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        val barModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        if (glass) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = barModifier.height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearWavyProgressIndicator(progress = { progress.value }, modifier = barModifier)
        }
    }
}

private const val REFRESH_FIRST_STEP = 0.33f
private const val REFRESH_CREEP_TO = 0.9f
private const val REFRESH_CREEP_MS = 15_000

/**
 * Pull to refresh for content that scrolls under the top bar: the indicator comes out from under
 * the bar ([topPadding]) rather than from behind it, and goes back once let go: the refresh
 * itself shows as a [RefreshBar]. Material 3 Expressive's morphing loading indicator; a plain
 * spinner with the glass look.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TollyPullToRefresh(
    onRefresh: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val glass = Tolly.glass
    PullToRefreshBox(
        // Never "refreshing" here: the indicator goes back once let go.
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val indicatorModifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding)
            if (glass) {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = false,
                    modifier = indicatorModifier,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = false, modifier = indicatorModifier)
            }
        },
        content = content,
    )
}
