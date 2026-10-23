package be.tolly.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** A new mail to fill in, with a text file attached when [attachmentName] is set. */
@Immutable
data class MailDraft(
    val to: String,
    val subject: String,
    val body: String,
    val attachmentName: String? = null,
    val attachment: String? = null,
)

/**
 * Opens a [MailDraft] in the Mail tab's Outlook, signed in already, and switches to that tab; the
 * user reads it over and sends it there. Null where there is no Mail tab; callers then fall back
 * to a mail app.
 */
val LocalOpenMailDraft = staticCompositionLocalOf<((MailDraft) -> Unit)?> { null }
