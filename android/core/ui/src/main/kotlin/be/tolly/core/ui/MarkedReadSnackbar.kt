package be.tolly.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import be.tolly.core.designsystem.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Shows "N announcements marked as read" with Undo for every batch of ids [markedRead] emits. */
@Composable
fun MarkedReadSnackbar(
    markedRead: Flow<List<String>>,
    snackbarHostState: SnackbarHostState,
    onUndo: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val currentOnUndo by rememberUpdatedState(onUndo)
    LaunchedEffect(markedRead) {
        coroutineScope {
            markedRead.collect { ids ->
                launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.announcements_marked_read, ids.size, ids.size),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnUndo(ids)
                }
            }
        }
    }
}
