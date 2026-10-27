package be.tolly.core.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.IconTileSize
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.model.FileViewer
import be.tolly.core.model.ViewerKind

/**
 * Asked the first time a file that Tolly can show opens: in Tolly's own viewer, or in another
 * app. The answer is kept (and can be changed in Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerChoiceSheet(onPick: (FileViewer) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val options = listOf(
        Triple(FileViewer.IN_APP, R.string.viewer_in_app, R.string.viewer_in_app_summary),
        Triple(FileViewer.EXTERNAL, R.string.viewer_external, R.string.viewer_external_summary),
    )
    TollySheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.viewer_choice_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.viewer_choice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            )
            options.forEachIndexed { index, (viewer, title, summary) ->
                TollyListItem(
                    modifier = Modifier.groupItem(
                        ItemPosition(index, options.size),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onPick(viewer)
                        },
                        separatorStart = GroupDefaults.separatorStart(IconTileSize),
                    ),
                    leading = {
                        if (viewer == FileViewer.IN_APP) IconTile(Icons.Outlined.Preview, TileColor.Blue) else IconTile(Icons.Outlined.Apps, TileColor.Indigo)
                    },
                    headline = { Text(stringResource(title)) },
                    supporting = { Text(stringResource(summary)) },
                )
            }
        }
    }
}

/** Intent action of the app's own file viewer (declared by the app module). */
const val ACTION_VIEW_FILE = "be.tolly.action.VIEW_FILE"

/** Whether Tolly's own viewer can show the file that [view] (an `ACTION_VIEW` intent) opens. */
internal fun viewsInApp(view: Intent): Boolean = ViewerKind.of(view.type, view.data?.lastPathSegment) != null

/** The same file as [view], in Tolly's own viewer. */
internal fun Context.inAppViewer(view: Intent): Intent = Intent(view).apply {
    action = ACTION_VIEW_FILE
    setPackage(packageName)
    component = null
    if (this@inAppViewer is Activity) flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
}
