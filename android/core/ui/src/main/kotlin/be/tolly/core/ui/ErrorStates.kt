package be.tolly.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.tolly.core.data.DataError
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.InfoBanner
import be.tolly.core.designsystem.component.MessageState

/** Opens the sign-in screen again; provided by the app scaffold while the user is signed in. */
val LocalRequestSignIn = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Full-area state for a list with nothing to show: the error message when loading failed,
 * otherwise [emptyTitle]. An expired session offers Sign in (retrying can't help then), other
 * errors offer Retry. [retryWhenEmpty] also offers Retry for an empty list without an error.
 */
@Composable
fun ListMessageState(
    icon: ImageVector,
    emptyTitle: String,
    error: DataError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryWhenEmpty: Boolean = false,
) {
    val requestSignIn = LocalRequestSignIn.current
    MessageState(
        icon = icon,
        title = if (error != null) stringResource(dataErrorMessage(error)) else emptyTitle,
        modifier = modifier,
        actionLabel = when {
            error == DataError.SESSION_EXPIRED -> stringResource(R.string.action_sign_in)
            error != null || retryWhenEmpty -> stringResource(R.string.action_retry)
            else -> null
        },
        onAction = if (error == DataError.SESSION_EXPIRED) requestSignIn else onRetry,
    )
}

/**
 * A banner above content that is still shown after a refresh failed, with Retry. Without content
 * the error is the [ListMessageState] itself, and an expired session has the app-wide banner, so
 * neither shows one here.
 */
@Composable
fun RefreshErrorBanner(error: DataError?, hasContent: Boolean, onRetry: () -> Unit) {
    if (error == null || error == DataError.SESSION_EXPIRED || !hasContent) return
    InfoBanner(
        text = stringResource(dataErrorMessage(error)),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        actionLabel = stringResource(R.string.action_retry),
        onAction = onRetry,
    )
}
