package be.tolly.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import be.tolly.core.data.MailAttachment

/** A new mail to fill in, with [attachments] to put in. */
@Immutable
data class MailDraft(
    val to: String,
    val subject: String,
    val body: String,
    val attachments: List<MailAttachment> = emptyList(),
)

/**
 * Opens a [MailDraft] in the Mail tab's Outlook, signed in already, and switches to that tab; the
 * user reads it over and sends it there. Null where there is no Mail tab; callers then fall back
 * to a mail app.
 */
val LocalOpenMailDraft = staticCompositionLocalOf<((MailDraft) -> Unit)?> { null }
