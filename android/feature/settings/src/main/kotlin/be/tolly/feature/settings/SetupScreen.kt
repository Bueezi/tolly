package be.tolly.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.IconTileSize
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SegmentLabel
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyBackButton
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollySwitch
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.TollyTheme
import be.tolly.core.designsystem.theme.supportsDynamicColor
import be.tolly.core.model.UserPreferences
import kotlin.math.roundToInt

private const val STEPS = 3
private const val STEP_LOOK = 0
private const val STEP_STYLE = 1

/**
 * The quick setup a new install goes through before signing in. First the user picks one of the
 * two looks from a live preview of each, side by side; nothing is picked for them, so Next waits
 * for a choice. Then the theme and shake to switch looks, then news notifications and how often
 * to check. Every choice takes effect at once (the look changes under the user's finger) and stays
 * changeable in Settings. [onDone] follows the last step or Skip; [onBack] leaves from the first step.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var lookPicked by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        if (step > 0) step-- else onBack()
    }
    val finish = {
        viewModel.finish()
        onDone()
    }
    BackHandler(onBack = back)

    TollyScaffold(
        topBar = {
            TollyTopBar(
                title = {
                    Text(
                        stringResource(
                            when (step) {
                                STEP_LOOK -> R.string.setup_look_title
                                STEP_STYLE -> R.string.setup_style_title
                                else -> R.string.setup_notifications_title
                            },
                        ),
                    )
                },
                subtitle = { Text(stringResource(R.string.setup_step, step + 1, STEPS)) },
                navigationIcon = { TollyBackButton(onClick = back) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(250)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(250)))
                        .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(150)))
                },
                label = "setup step",
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = padding.calculateTopPadding() + 8.dp, bottom = 16.dp),
                    ) {
                        when (page) {
                            STEP_LOOK -> LookStep(state.preferences, picked = lookPicked) { glass ->
                                lookPicked = true
                                viewModel.setGlass(glass)
                            }
                            STEP_STYLE -> StyleStep(state, viewModel)
                            else -> NotificationsStep(state, viewModel)
                        }
                    }
                }
            }
            Row(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = finish) { Text(stringResource(R.string.action_skip)) }
                Box(Modifier.weight(1f))
                Button(
                    onClick = { if (step < STEPS - 1) step++ else finish() },
                    enabled = step != STEP_LOOK || lookPicked,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(if (step < STEPS - 1) R.string.action_next else R.string.setup_finish))
                }
            }
        }
    }
}

@Composable
private fun StepIntro(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** The two looks side by side, each shown in itself; until one is [picked], neither is marked. */
@Composable
private fun LookStep(preferences: UserPreferences, picked: Boolean, onPick: (glass: Boolean) -> Unit) {
    StepIntro(R.string.setup_look_pick_body)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LookCard(
            glass = false,
            preferences = preferences,
            selected = picked && !preferences.glass,
            title = stringResource(R.string.setup_look_material),
            summary = stringResource(R.string.setup_look_material_summary),
            onSelect = { onPick(false) },
            modifier = Modifier.weight(1f),
        )
        LookCard(
            glass = true,
            preferences = preferences,
            selected = picked && preferences.glass,
            title = stringResource(R.string.settings_glass),
            summary = stringResource(R.string.setup_look_glass_short),
            onSelect = { onPick(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One look to pick: a small screen drawn in that look (its colours, lists, switch and segmented
 * control, whichever look the setup itself is in), its name and a line about it.
 */
@Composable
private fun LookCard(
    glass: Boolean,
    preferences: UserPreferences,
    selected: Boolean,
    title: String,
    summary: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.extraLarge
    val border by animateDpAsState(if (selected) 3.dp else 1.dp, label = "look border")
    Column(
        modifier
            .clip(shape)
            .border(border, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect()
                },
            )
            .padding(8.dp),
    ) {
        // Both previews are drawn at the same size, whatever each look's rows take, and the
        // summary keeps room for three lines, so the two cards are equally tall.
        TollyTheme(preferences.copy(glass = glass)) {
            LookPreview(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clearAndSetSemantics {}.drawnAt(PreviewWidth, PreviewHeight))
        }
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 3,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
    }
}

/** A few of the app's own parts, in whichever look the theme around it has; it doesn't react to touch. */
@Composable
private fun LookPreview(modifier: Modifier = Modifier) {
    Column(modifier.background(Tolly.colors.screen).padding(vertical = 12.dp).fillMaxHeight()) {
        Text(
            stringResource(R.string.nav_courses),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TollySegmented(
            count = 2,
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) { index -> SegmentLabel(stringResource(if (index == 0) R.string.course_tab_content else R.string.course_tab_announcements)) }
        Box(Modifier.height(8.dp))
        val rows = listOf(
            Triple(Icons.Outlined.Notifications, TileColor.Red, R.string.nav_announcements_short),
            Triple(Icons.Outlined.Mail, TileColor.Blue, R.string.nav_mail),
        )
        rows.forEachIndexed { index, (icon, color, label) ->
            TollyListItem(
                modifier = Modifier.groupItem(ItemPosition(index, rows.size), separatorStart = GroupDefaults.separatorStart(IconTileSize)),
                leading = { IconTile(icon, color) },
                headline = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailing = { TollySwitch(checked = index == 0, onCheckedChange = null) },
            )
        }
    }
}

/** How big a [LookPreview] is laid out, like a small phone, before it is shrunk to fit its card. */
private val PreviewWidth = 300.dp
private val PreviewHeight = 268.dp

/** Lays this out [width] by [height], whatever room it gets, and scales it to fill the width it was given. */
private fun Modifier.drawnAt(width: Dp, height: Dp): Modifier = layout { measurable, constraints ->
    val laidOut = width.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(laidOut, height.roundToPx()))
    val scale = constraints.maxWidth / laidOut.toFloat()
    layout(constraints.maxWidth, (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** The theme, shaking to switch looks and (Material) wallpaper colours. */
@Composable
private fun StyleStep(state: SetupUiState, viewModel: SetupViewModel) {
    val glass = state.preferences.glass
    StepIntro(R.string.setup_look_body)
    SettingsGroup(
        R.string.settings_appearance,
        { position -> ThemeRow(position, state.preferences.themeMode, viewModel::setThemeMode) },
        { position ->
            SwitchRow(
                position = position,
                icon = Icons.Outlined.Vibration,
                color = TileColor.Blue,
                title = stringResource(R.string.settings_shake_look),
                summary = stringResource(R.string.settings_shake_look_summary),
                checked = state.preferences.shakeToSwitchLook,
                onCheckedChange = viewModel::setShakeToSwitchLook,
            )
        },
        // The glass look has its own colours.
        if (supportsDynamicColor && !glass) {
            { position ->
                SwitchRow(
                    position = position,
                    icon = Icons.Outlined.Wallpaper,
                    color = TileColor.Purple,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = state.preferences.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun NotificationsStep(state: SetupUiState, viewModel: SetupViewModel) {
    StepIntro(R.string.setup_notifications_body)
    NotificationsGroup(state.preferences, viewModel::setNotifyAnnouncements, viewModel::setNotifyMail, viewModel::setNewsCheckMinutes)
}
