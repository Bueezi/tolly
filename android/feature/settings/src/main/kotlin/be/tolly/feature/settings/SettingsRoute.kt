package be.tolly.feature.settings

import be.tolly.core.ui.LocalOpenMailDraft
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.IntentCompat
import be.tolly.core.data.LogExporter
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Settings
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.PaneNavigationIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.core.auth.KuLoket
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyScaffold
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollySwitch
import be.tolly.core.designsystem.component.TollyTopBar
import be.tolly.core.designsystem.component.fileSize
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.component.scrollingContentPadding
import be.tolly.core.designsystem.theme.GlassTier
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.deviceGlassTier
import be.tolly.core.designsystem.theme.supportsDynamicColor
import be.tolly.core.model.ThemeMode

/**
 * The You tab's list: the account, the user's grades and downloads, and the way to Settings and
 * to feedback. Settings themselves are one tap deeper ([SettingsPane]), so this stays short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouList(
    /** The section open beside the list (on a wide screen), to mark its row. */
    selected: YouSection? = null,
    onOpen: (YouSection) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var askStudentNumber by rememberSaveable { mutableStateOf(false) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    val feedbackDraft = rememberFeedbackDraft()
    val openMailDraft = LocalOpenMailDraft.current
    val versionName = rememberVersionName()
    val snackbarHostState = remember { SnackbarHostState() }
    val copy = rememberCopyToClipboard(snackbarHostState)
    StartIntents(viewModel)

    TollyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { TollyTopBar(title = { Text(stringResource(R.string.nav_you)) }) },
    ) { padding ->
        SettingsColumn(padding) {
            state.user?.let { user ->
                // Toledo's user name is a q-number; the student number comes from KU Loket.
                val number = state.preferences.studentNumber
                    ?: listOfNotNull(user.studentId, user.userName).firstNotNullOfOrNull { KuLoket.studentNumber(it)?.takeIf(::isStudentNumber) }
                AccountCard(
                    name = user.displayName,
                    seed = user.id,
                    studentNumber = number,
                    email = user.email?.takeIf { it.isNotBlank() },
                    onCopy = copy,
                    onAddStudentNumber = { askStudentNumber = true },
                )
            }

            SettingsGroup(
                R.string.you_studies,
                { position ->
                    SectionRow(
                        position = position,
                        icon = Icons.Outlined.Grading,
                        color = TileColor.Green,
                        title = stringResource(R.string.course_tab_grades),
                        summary = stringResource(R.string.you_grades_summary),
                        selected = selected == YouSection.GRADES,
                        onClick = { onOpen(YouSection.GRADES) },
                    )
                },
                { position ->
                    SectionRow(
                        position = position,
                        icon = Icons.Outlined.Download,
                        color = TileColor.Blue,
                        title = stringResource(R.string.nav_downloads),
                        summary = if (state.downloadsSize > 0) {
                            stringResource(R.string.settings_downloads_size, fileSize(state.downloadsSize))
                        } else {
                            stringResource(R.string.you_downloads_summary)
                        },
                        selected = selected == YouSection.DOWNLOADS,
                        onClick = { onOpen(YouSection.DOWNLOADS) },
                    )
                },
            )

            SettingsGroup(
                R.string.you_app,
                { position ->
                    SectionRow(
                        position = position,
                        icon = Icons.Outlined.Settings,
                        color = TileColor.Gray,
                        title = stringResource(R.string.settings_title),
                        summary = stringResource(R.string.you_settings_summary),
                        selected = selected == YouSection.SETTINGS,
                        onClick = { onOpen(YouSection.SETTINGS) },
                    )
                },
                { position ->
                    ActionRow(
                        position = position,
                        icon = Icons.Outlined.Feedback,
                        color = TileColor.Orange,
                        title = stringResource(R.string.feedback_title),
                        summary = stringResource(R.string.feedback_summary),
                        onClick = { showFeedback = true },
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
            TollyListItem(
                modifier = Modifier.groupItem(ItemPosition.Single, enabled = !state.signingOut, onClick = { confirmSignOut = true }),
                leading = { IconTile(Icons.AutoMirrored.Outlined.Logout, TileColor.Red) },
                headline = { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) },
            )
        }
    }

    if (showFeedback) {
        FeedbackSheet(
            draft = feedbackDraft,
            onSend = { subject, message, attachLogs ->
                showFeedback = false
                // In the Mail tab's Outlook, already signed in to the KU Leuven mailbox; without
                // one, the user's mail app. Either way the log goes as a file.
                if (openMailDraft != null) {
                    viewModel.feedbackMail(subject, message, attachLogs, onReady = openMailDraft)
                } else {
                    viewModel.sendFeedback(subject, message, attachLogs)
                }
            },
            onOpenIssue = { title, message ->
                showFeedback = false
                viewModel.openIssue(title, message, versionName)
            },
            onDismiss = { showFeedback = false },
        )
    }
    if (askStudentNumber) {
        StudentNumberDialog(
            onSave = {
                viewModel.setStudentNumber(it)
                askStudentNumber = false
            },
            onDismiss = { askStudentNumber = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = R.string.settings_sign_out_title,
            body = R.string.settings_sign_out_body,
            confirm = R.string.settings_sign_out,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** Settings: the look, notifications, courses, storage and the app itself, opened from the You tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPane(
    /** How the pane is left: it opens from the You tab, beside it or on top of it. */
    navigation: PaneNavigation = PaneNavigation.NONE,
    onNavigate: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDeleteDownloads by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    val versionName = rememberVersionName()
    StartIntents(viewModel)

    val glass = Tolly.glass
    TollyScaffold(
        topBar = {
            TollyTopBar(
                detail = true,
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { PaneNavigationIcon(navigation, onNavigate) },
            )
        },
    ) { padding ->
        SettingsColumn(padding) {
            SettingsGroup(
                R.string.settings_appearance,
                { position -> ThemeRow(position, state.preferences.themeMode, viewModel::setThemeMode) },
                // Only matters when the app can be dark.
                if (state.preferences.themeMode != ThemeMode.LIGHT) {
                    { position ->
                        SwitchRow(
                            position = position,
                            icon = Icons.Outlined.Contrast,
                            color = TileColor.Gray,
                            title = stringResource(R.string.settings_amoled),
                            summary = stringResource(R.string.settings_amoled_summary),
                            checked = state.preferences.amoled,
                            onCheckedChange = viewModel::setAmoled,
                        )
                    }
                } else {
                    null
                },
                { position ->
                    SwitchRow(
                        position = position,
                        icon = Icons.Outlined.BlurOn,
                        color = TileColor.Blue,
                        title = stringResource(R.string.settings_glass),
                        summary = stringResource(R.string.settings_glass_summary) + if (state.preferences.glass) {
                            "\n" + stringResource(
                                when (deviceGlassTier) {
                                    GlassTier.LIQUID -> R.string.settings_glass_tier_liquid
                                    GlassTier.BLUR -> R.string.settings_glass_tier_blur
                                    GlassTier.TINT -> R.string.settings_glass_tier_tint
                                },
                            )
                        } else {
                            ""
                        },
                        checked = state.preferences.glass,
                        onCheckedChange = viewModel::setGlass,
                    )
                },
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
                            icon = Icons.Outlined.Palette,
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
                { position -> LanguageRow(position) },
            )

            NotificationsGroup(
                state.preferences,
                viewModel::setNotifyAnnouncements,
                viewModel::setNotifyMail,
                viewModel::setNewsCheckMinutes,
                onTabBadgesChange = viewModel::setTabBadges,
            )

            SettingsGroup(
                R.string.settings_courses,
                { position ->
                    SwitchRow(
                        position = position,
                        icon = Icons.Outlined.Groups,
                        color = TileColor.Green,
                        title = stringResource(R.string.courses_show_communities),
                        summary = stringResource(R.string.settings_communities_summary),
                        checked = state.preferences.showCommunities,
                        onCheckedChange = viewModel::setShowCommunities,
                    )
                },
                { position ->
                    SwitchRow(
                        position = position,
                        icon = Icons.Outlined.History,
                        color = TileColor.Orange,
                        title = stringResource(R.string.courses_show_past),
                        summary = stringResource(R.string.settings_past_summary),
                        checked = state.preferences.showPastCourses,
                        onCheckedChange = viewModel::setShowPastCourses,
                    )
                },
            )

            SettingsGroup(
                R.string.settings_storage,
                { position -> FileViewerRow(position, state.preferences.fileViewer, viewModel::setFileViewer) },
                *copyToDownloadsRows(
                    preferences = state.preferences,
                    folderLabel = state.downloadsFolder,
                    onCopyChange = viewModel::setCopyToDownloads,
                    onFolderChange = viewModel::setDownloadsFolder,
                    onCopyToFolder = viewModel::copyToFolder,
                ),
                { position ->
                    ActionRow(
                        position = position,
                        icon = Icons.Outlined.Delete,
                        color = TileColor.Pink,
                        title = stringResource(R.string.settings_delete_downloads),
                        summary = stringResource(R.string.settings_downloads_size, fileSize(state.downloadsSize)),
                        onClick = { confirmDeleteDownloads = true },
                    )
                },
                { position ->
                    ActionRow(
                        position = position,
                        icon = Icons.Outlined.CleaningServices,
                        color = TileColor.Gray,
                        title = stringResource(R.string.settings_clear_cache),
                        summary = stringResource(R.string.settings_clear_cache_summary),
                        onClick = viewModel::clearCache,
                    )
                },
            )

            SettingsGroup(
                R.string.settings_about,
                { position -> AboutRow(position, versionName) },
                { position ->
                    ActionRow(
                        position = position,
                        icon = Icons.Outlined.Gavel,
                        color = TileColor.Purple,
                        title = stringResource(R.string.settings_license),
                        summary = stringResource(R.string.settings_license_summary),
                        onClick = { showLicense = true },
                    )
                },
                { position ->
                    ActionRow(
                        position = position,
                        icon = Icons.Outlined.BugReport,
                        color = TileColor.Indigo,
                        title = stringResource(R.string.settings_export_logs),
                        summary = stringResource(R.string.settings_export_logs_summary),
                        onClick = viewModel::exportLogs,
                    )
                },
            )
        }
    }

    if (showLicense) LicenseSheet(onDismiss = { showLicense = false })
    if (confirmDeleteDownloads) {
        ConfirmDialog(
            title = R.string.downloads_delete_all_title,
            body = R.string.downloads_delete_all_body,
            confirm = R.string.action_delete,
            onConfirm = {
                confirmDeleteDownloads = false
                viewModel.deleteDownloads()
            },
            onDismiss = { confirmDeleteDownloads = false },
        )
    }
}

/** The settings' scrolling column, at most 720 dp wide; it scrolls under the top bar. */
@Composable
private fun SettingsColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(scrollingContentPadding(padding, top = 8.dp, bottom = 24.dp)),
            content = content,
        )
    }
}

/** Starts what the view model asks for, like the share sheet or the mail app. */
@Composable
private fun StartIntents(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.intents.collect { intent ->
            val fallback = IntentCompat.getParcelableExtra(intent, LogExporter.EXTRA_FALLBACK, Intent::class.java)
            val started = listOfNotNull(intent, fallback).any { candidate ->
                try {
                    context.startActivity(candidate)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }
            if (!started) Toast.makeText(context, R.string.feedback_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return rememberSaveable {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
