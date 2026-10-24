package be.tolly.feature.settings

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.SegmentLabel
import be.tolly.core.designsystem.component.TollySegmented
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.TollySwitch
import be.tolly.core.ui.withHaptic

/**
 * Feedback goes without a server of our own. By default it is an e-mail: private, so the log can
 * go with it, and it works for everyone with a mail app. A GitHub issue is the alternative for
 * whoever wants to discuss it in public; the repository is public, so it never carries the log.
 */
internal const val FEEDBACK_EMAIL = "hello@tolly.be"

internal const val GITHUB_REPOSITORY = "https://github.com/Bueezi/tolly"

/** What the feedback is about; it starts the subject so mails can be sorted. */
enum class FeedbackKind { BUG, IDEA, OTHER }

/** A new issue with the title and message filled in, and the app and Android version below. */
internal fun githubIssueUri(title: String, message: String, versionName: String): Uri =
    Uri.parse("$GITHUB_REPOSITORY/issues/new").buildUpon()
        .appendQueryParameter("title", title)
        .appendQueryParameter(
            "body",
            message.trimEnd() + "\n\n---\nTolly $versionName, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
        )
        .build()

/**
 * What the user wrote in the feedback sheet. It outlives the sheet: sending doesn't clear it, nor
 * does closing the sheet, so a mail that didn't go out or a sheet swiped away by mistake doesn't
 * cost the text. Only [clear] (the sheet's "Clear draft") empties it.
 */
@Stable
internal class FeedbackDraft(kind: FeedbackKind = FeedbackKind.BUG, message: String = "", attachLogs: Boolean = true) {
    var kind by mutableStateOf(kind)
    var message by mutableStateOf(message)
    var attachLogs by mutableStateOf(attachLogs)

    fun clear() {
        kind = FeedbackKind.BUG
        message = ""
        attachLogs = true
    }

    companion object {
        val Saver = listSaver<FeedbackDraft, Any>(
            save = { listOf(it.kind.name, it.message, it.attachLogs) },
            restore = { FeedbackDraft(FeedbackKind.valueOf(it[0] as String), it[1] as String, it[2] as Boolean) },
        )
    }
}

@Composable
internal fun rememberFeedbackDraft(): FeedbackDraft = rememberSaveable(saver = FeedbackDraft.Saver) { FeedbackDraft() }

/**
 * Asks what the feedback is about and what the user wants to say, then hands it to Outlook or
 * their mail app ([onSend], with the log if they leave it attached) or to a public GitHub issue
 * ([onOpenIssue]). The text stays in [draft].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackSheet(
    draft: FeedbackDraft,
    onSend: (subject: String, message: String, attachLogs: Boolean) -> Unit,
    onOpenIssue: (title: String, message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var kind by draft::kind
    var message by draft::message
    var attachLogs by draft::attachLogs
    val kinds = FeedbackKind.entries
    val kindLabel = stringResource(
        when (kind) {
            FeedbackKind.BUG -> R.string.feedback_kind_bug
            FeedbackKind.IDEA -> R.string.feedback_kind_idea
            FeedbackKind.OTHER -> R.string.feedback_kind_other
        },
    )
    // The first line of the message names the issue; the mail subject is sorted by kind.
    val firstLine = message.trim().lineSequence().firstOrNull().orEmpty().take(80)
    TollySheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleLarge)
            TollySegmented(
                count = kinds.size,
                selectedIndex = kinds.indexOf(kind),
                onSelect = { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    kind = kinds[index]
                },
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                SegmentLabel(
                    stringResource(
                        when (kinds[index]) {
                            FeedbackKind.BUG -> R.string.feedback_kind_bug
                            FeedbackKind.IDEA -> R.string.feedback_kind_idea
                            FeedbackKind.OTHER -> R.string.feedback_kind_other
                        },
                    ),
                )
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = {
                    Text(stringResource(if (kind == FeedbackKind.BUG) R.string.feedback_hint_bug else R.string.feedback_hint))
                },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = attachLogs,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            attachLogs = it
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.feedback_attach_logs), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.feedback_attach_logs_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TollySwitch(checked = attachLogs, onCheckedChange = null)
            }
            Button(
                onClick = { onSend("Tolly: $kindLabel" + if (firstLine.isNotEmpty()) " – $firstLine" else "", message, attachLogs) },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.feedback_send))
            }
            TextButton(
                onClick = { onOpenIssue(firstLine.ifEmpty { kindLabel }, message) },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feedback_github))
            }
            if (message.isNotEmpty()) {
                TextButton(
                    onClick = withHaptic(HapticFeedbackType.Confirm, draft::clear),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.feedback_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
