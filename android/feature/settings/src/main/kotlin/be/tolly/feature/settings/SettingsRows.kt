package be.tolly.feature.settings

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.IconTileSize
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.SegmentLabel
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.TollySlider
import be.tolly.core.designsystem.component.TollySwitch
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.model.NEWS_CHECK_INTERVALS
import be.tolly.core.model.ThemeMode
import be.tolly.core.model.UserPreferences
import be.tolly.core.ui.AppLanguage
import be.tolly.core.ui.canSaveCopies
import be.tolly.core.ui.rememberEnableNotifications
import kotlinx.coroutines.launch

/** A titled group of settings rows; null rows are left out. */
@Composable
internal fun SettingsGroup(title: Int, vararg rows: (@Composable (ItemPosition) -> Unit)?) {
    val shown = rows.filterNotNull()
    SectionHeader(stringResource(title))
    shown.forEachIndexed { index, row -> row(ItemPosition(index, shown.size)) }
}

/** The theme, as a segmented choice under its title (like RikkaHub's colour mode). */
@Composable
internal fun ThemeRow(position: ItemPosition, selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val modes = ThemeMode.entries
    Column(Modifier.groupItem(position).padding(bottom = 16.dp)) {
        TollyListItem(
            leading = { IconTile(Icons.Outlined.DarkMode, TileColor.Indigo) },
            headline = { Text(stringResource(R.string.settings_theme)) },
        )
        TollySegmented(
            count = modes.size,
            selectedIndex = modes.indexOf(selected),
            onSelect = { index ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(modes[index])
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { index ->
            SegmentLabel(
                stringResource(
                    when (modes[index]) {
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                    },
                ),
            )
        }
    }
}

@Composable
internal fun SwitchRow(
    position: ItemPosition,
    icon: ImageVector,
    color: TileColor,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val toggle: () -> Unit = {
        haptics.performHapticFeedback(if (!checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(!checked)
    }
    TollyListItem(
        modifier = Modifier
            .groupItem(position, onClick = toggle, role = Role.Switch, separatorStart = GroupDefaults.separatorStart(IconTileSize))
            .semantics { toggleableState = ToggleableState(checked) },
        leading = { IconTile(icon, color) },
        headline = { Text(title) },
        supporting = { Text(summary) },
        trailing = { TollySwitch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
internal fun ActionRow(position: ItemPosition, icon: ImageVector, color: TileColor, title: String, summary: String, onClick: () -> Unit) {
    TollyListItem(
        modifier = Modifier.groupItem(position, onClick = onClick, separatorStart = GroupDefaults.separatorStart(IconTileSize)),
        leading = { IconTile(icon, color) },
        headline = { Text(title) },
        supporting = { Text(summary) },
    )
}

/** What the You tab opens from its list. */
enum class YouSection { GRADES, DOWNLOADS, SETTINGS }

/** A row that opens a screen of its own, marked while that screen is open beside the list. */
@Composable
internal fun SectionRow(
    position: ItemPosition,
    icon: ImageVector,
    color: TileColor,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TollyListItem(
        modifier = Modifier.groupItem(position, selected = selected, onClick = onClick, separatorStart = GroupDefaults.separatorStart(IconTileSize)),
        leading = { IconTile(icon, color) },
        headline = { Text(title) },
        supporting = { Text(summary) },
        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
    )
}

/**
 * How often the background check for announcements runs, as a slider over the big steps of
 * [NEWS_CHECK_INTERVALS], with the choice next to the title (like ObtainX). It saves when the
 * finger lets go, so the schedule changes once.
 */
@Composable
internal fun NewsIntervalRow(position: ItemPosition, minutes: Int, onChange: (Int) -> Unit) {
    val saved = NEWS_CHECK_INTERVALS.indexOf(minutes).coerceAtLeast(0)
    var index by remember(saved) { mutableIntStateOf(saved) }
    val currentOnChange by rememberUpdatedState(onChange)
    Column(Modifier.groupItem(position).padding(bottom = 12.dp)) {
        TollyListItem(
            leading = { IconTile(Icons.Outlined.Schedule, TileColor.Orange) },
            headline = { Text(stringResource(R.string.settings_news_interval)) },
            trailing = {
                Text(
                    intervalText(NEWS_CHECK_INTERVALS[index]),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        TollySlider(
            count = NEWS_CHECK_INTERVALS.size,
            value = index,
            onValueChange = { index = it },
            onValueChangeFinished = { currentOnChange(NEWS_CHECK_INTERVALS[index]) },
            valueLabel = { intervalText(NEWS_CHECK_INTERVALS[it]) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

/** "15 min", "1 hour", "6 hours": short, to sit next to the title. */
@Composable
internal fun intervalText(minutes: Int): String =
    if (minutes % 60 == 0) {
        pluralStringResource(R.plurals.settings_interval_hours, minutes / 60, minutes / 60)
    } else {
        pluralStringResource(R.plurals.settings_interval_minutes, minutes, minutes)
    }

/**
 * Where copies of downloads go: tap to pick a folder with the system's folder picker; with a
 * picked folder, a button goes back to Downloads/Tolly.
 */
@Composable
internal fun DownloadsFolderRow(position: ItemPosition, label: String, custom: Boolean, onPick: (Uri?) -> Unit) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onPick(uri) }
    TollyListItem(
        modifier = Modifier.groupItem(position, onClick = { pickFolder.launch(null) }, separatorStart = GroupDefaults.separatorStart(IconTileSize)),
        leading = { IconTile(Icons.Outlined.Folder, TileColor.Teal) },
        headline = { Text(stringResource(R.string.settings_downloads_folder)) },
        supporting = { Text(label) },
        trailing = if (custom) {
            {
                IconButton(onClick = { onPick(null) }) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.settings_downloads_folder_reset))
                }
            }
        } else {
            null
        },
    )
}

/**
 * The Notifications group of Settings and the quick setup: news notifications on or off (asking
 * for the permission when turned on) and, while on, how often to check.
 */
@Composable
internal fun NotificationsGroup(
    preferences: UserPreferences,
    onNotifyChange: (Boolean) -> Unit,
    onNotifyMailChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    /** Turns the tabs' unread counts on or off; that row only shows in Settings. */
    onTabBadgesChange: ((Boolean) -> Unit)? = null,
) {
    val enableNotifications = rememberEnableNotifications { granted -> if (granted) onNotifyChange(true) }
    val enableMailNotifications = rememberEnableNotifications { granted -> if (granted) onNotifyMailChange(true) }
    SettingsGroup(
        R.string.settings_notifications,
        { position ->
            SwitchRow(
                position = position,
                icon = Icons.Outlined.NotificationsActive,
                color = TileColor.Red,
                title = stringResource(R.string.settings_notify_announcements),
                summary = stringResource(R.string.settings_notify_announcements_summary),
                checked = preferences.notifyAnnouncements,
                onCheckedChange = { on -> if (on) enableNotifications() else onNotifyChange(false) },
            )
        },
        { position ->
            SwitchRow(
                position = position,
                icon = Icons.Outlined.Mail,
                color = TileColor.Blue,
                title = stringResource(R.string.settings_notify_mail),
                summary = stringResource(R.string.settings_notify_mail_summary),
                checked = preferences.notifyMail,
                onCheckedChange = { on -> if (on) enableMailNotifications() else onNotifyMailChange(false) },
            )
        },
        if (preferences.notifyAnnouncements || preferences.notifyMail) {
            { position -> NewsIntervalRow(position, preferences.newsCheckMinutes, onIntervalChange) }
        } else {
            null
        },
        onTabBadgesChange?.let { onChange ->
            { position ->
                SwitchRow(
                    position = position,
                    icon = Icons.Outlined.Tag,
                    color = TileColor.Gray,
                    title = stringResource(R.string.settings_tab_badges),
                    summary = stringResource(R.string.settings_tab_badges_summary),
                    checked = preferences.tabBadges,
                    onCheckedChange = onChange,
                )
            }
        },
    )
}

/**
 * The rows for copies of downloads, for a [SettingsGroup]: the switch and, while copies are on
 * (or can't be made before a folder is picked), the folder row. Before Android 10 turning copies
 * on picks a folder first and then [onCopyToFolder]s; see [canSaveCopies].
 */
@Composable
internal fun copyToDownloadsRows(
    preferences: UserPreferences,
    folderLabel: String,
    onCopyChange: (Boolean) -> Unit,
    onFolderChange: (Uri?) -> Unit,
    onCopyToFolder: (Uri) -> Unit,
): Array<(@Composable (ItemPosition) -> Unit)?> {
    val canCopy = canSaveCopies(preferences.downloadsFolder)
    val pickFolderAndCopy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onCopyToFolder(uri)
    }
    return arrayOf(
        { position ->
            SwitchRow(
                position = position,
                icon = Icons.Outlined.SaveAlt,
                color = TileColor.Teal,
                title = stringResource(R.string.settings_copy_to_downloads),
                summary = stringResource(R.string.settings_copy_to_downloads_summary, folderLabel),
                checked = preferences.copyToDownloads && canCopy,
                onCheckedChange = { on -> if (!on || canCopy) onCopyChange(on) else pickFolderAndCopy.launch(null) },
            )
        },
        if (preferences.copyToDownloads || !canCopy) {
            { position ->
                DownloadsFolderRow(
                    position = position,
                    label = folderLabel,
                    custom = preferences.downloadsFolder != null,
                    onPick = onFolderChange,
                )
            }
        } else {
            null
        },
    )
}

/** `r0123456`: what KU Leuven students know as their student number. */
internal fun isStudentNumber(value: String): Boolean = Regex("""[rs]\d{7}""").matches(value)

/**
 * The signed-in user: avatar and name, with the student number and email beside it (students
 * often have to type them somewhere): holding or tapping one copies it, which the clipboard icon
 * hints at. While the student number is unknown, its line asks for it instead
 * ([onAddStudentNumber]).
 */
@Composable
internal fun AccountCard(
    name: String,
    seed: String,
    studentNumber: String?,
    email: String?,
    onCopy: (label: String, value: String) -> Unit,
    onAddStudentNumber: () -> Unit,
) {
    Row(
        Modifier.groupItem(ItemPosition.Single).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CourseAvatar(name = name, seed = seed, size = 64.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val numberLabel = stringResource(R.string.settings_student_number)
            if (studentNumber != null) {
                CopyLine(studentNumber, numberLabel) { onCopy(numberLabel, studentNumber) }
            } else {
                InfoLine(
                    text = stringResource(R.string.settings_student_number_add),
                    icon = Icons.Outlined.Add,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onAddStudentNumber,
                )
            }
            if (email != null) {
                val emailLabel = stringResource(R.string.settings_email)
                CopyLine(email, emailLabel) { onCopy(emailLabel, email) }
            }
        }
    }
}

/** A line of the account card that copies [text] when held (or tapped). */
@Composable
private fun CopyLine(text: String, label: String, onCopy: () -> Unit) {
    InfoLine(
        text = text,
        icon = Icons.Outlined.ContentCopy,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onCopy,
        onLongClick = onCopy,
        actionLabel = stringResource(R.string.action_copy) + " " + label,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoLine(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Row(
        Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onClickLabel = actionLabel, onLongClick = onLongClick, onLongClickLabel = actionLabel)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
    }
}

/** Asks for the student number when it couldn't be found: `r` and seven digits. */
@Composable
internal fun StudentNumberDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.trim().lowercase()
    val valid = isStudentNumber(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_student_number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_student_number_hint))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(12) },
                    singleLine = true,
                    placeholder = { Text("r0123456") },
                    isError = value.isNotEmpty() && !valid,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Copies text with a confirming tick. Android 13 and up show their own "copied" note, so the
 * snackbar is only for older versions.
 */
@Composable
internal fun rememberCopyToClipboard(snackbarHostState: SnackbarHostState): (label: String, value: String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, context, haptics, scope) {
        { label, value ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbarHostState.showSnackbar(context.getString(R.string.copied, label))
                }
            }
        }
    }
}

/** The app's language: the system's, English or Dutch, as a segmented choice like the theme. */
@Composable
internal fun LanguageRow(position: ItemPosition) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val languages = AppLanguage.entries
    val selected = remember(context) { AppLanguage.current(context) }
    Column(Modifier.groupItem(position).padding(bottom = 16.dp)) {
        TollyListItem(
            leading = { IconTile(Icons.Outlined.Translate, TileColor.Blue) },
            headline = { Text(stringResource(R.string.settings_language)) },
        )
        TollySegmented(
            count = languages.size,
            selectedIndex = languages.indexOf(selected),
            onSelect = { index ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                if (activity != null && languages[index] != selected) AppLanguage.set(activity, languages[index])
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { index ->
            SegmentLabel(
                stringResource(
                    when (languages[index]) {
                        AppLanguage.SYSTEM -> R.string.settings_language_system
                        AppLanguage.ENGLISH -> R.string.language_english
                        AppLanguage.DUTCH -> R.string.language_dutch
                    },
                ),
            )
        }
    }
}

/**
 * The app itself: its icon, name and version, a few words about it and the disclaimer. Tapping it
 * opens the source code on GitHub.
 */
@Composable
internal fun AboutRow(position: ItemPosition, versionName: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val icon = remember(context) {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toImageBitmap() }.getOrNull()
    }
    TollyListItem(
        modifier = Modifier.groupItem(
            position,
            onClick = { runCatching { uriHandler.openUri(GITHUB_REPOSITORY) } },
            onClickLabel = stringResource(R.string.settings_about_source),
            separatorStart = GroupDefaults.separatorStart(IconTileSize),
        ),
        leading = {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(IconTileSize).clip(RoundedCornerShape(IconTileSize / 4)))
            }
        },
        headline = { Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.titleMedium) },
        supporting = {
            Column(Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.settings_about_text))
                Text(
                    stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
    )
}

/** The license, from `res/raw/license.txt`, in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicenseSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(context) {
        runCatching { context.resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    TollySheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.titleLarge)
            Text(text.trim(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Drawable.toImageBitmap(size: Int = 192): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
