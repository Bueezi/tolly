package be.tolly.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.IconTileSize
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyBackButton
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.supportsDynamicColor

private const val STEPS = 2

/**
 * The quick setup a new install goes through before signing in: the look, theme and shake to
 * switch looks, then news notifications and how often to check. Every choice
 * takes effect at once (the look changes under the user's finger) and stays changeable in
 * Settings. [onDone] follows the last step or Skip; [onBack] leaves from the first step.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
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
                            if (step == 0) R.string.setup_look_title else R.string.setup_notifications_title,
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
                        if (page == 0) LookStep(state, viewModel) else NotificationsStep(state, viewModel)
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
                Button(onClick = { if (step < STEPS - 1) step++ else finish() }, modifier = Modifier.height(48.dp)) {
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

@Composable
private fun LookStep(state: SetupUiState, viewModel: SetupViewModel) {
    val glass = state.preferences.glass
    StepIntro(R.string.setup_look_body)
    SettingsGroup(
        R.string.settings_appearance,
        { position ->
            LookOption(
                position = position,
                icon = Icons.Outlined.Palette,
                color = TileColor.Purple,
                title = stringResource(R.string.setup_look_material),
                summary = stringResource(R.string.setup_look_material_summary),
                selected = !glass,
                onSelect = { viewModel.setGlass(false) },
            )
        },
        { position ->
            LookOption(
                position = position,
                icon = Icons.Outlined.BlurOn,
                color = TileColor.Blue,
                title = stringResource(R.string.settings_glass),
                summary = stringResource(R.string.settings_glass_summary),
                selected = glass,
                onSelect = { viewModel.setGlass(true) },
            )
        },
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
        // The glass look has its own (iOS) colours.
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

/** One of the two looks, picked like an iOS list choice: a check on the chosen one. */
@Composable
private fun LookOption(
    position: ItemPosition,
    icon: ImageVector,
    color: TileColor,
    title: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    TollyListItem(
        modifier = Modifier.groupItem(
            position,
            // iOS marks the choice with the check alone.
            selected = selected && !Tolly.glass,
            onClick = {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect()
            },
            role = Role.RadioButton,
            separatorStart = GroupDefaults.separatorStart(IconTileSize),
        ),
        leading = { IconTile(icon, color) },
        headline = { Text(title) },
        supporting = { Text(summary) },
        trailing = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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
