package be.tolly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.model.Notices

/** Each notice's title and text, by its number (see [Notices]). Old ones stay, for the test. */
internal val noticeTexts: Map<Int, Pair<Int, Int>> = mapOf(
    1 to (R.string.notice_1_title to R.string.notice_1_body),
    2 to (R.string.notice_2_title to R.string.notice_2_body),
    3 to (R.string.notice_3_title to R.string.notice_3_body),
)

/** A one-time notice ([Notices]): its title and text, and a button that puts it away for good. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeSheet(id: Int, onDismiss: () -> Unit) {
    val text = noticeTexts[id]
    if (text == null) {
        LaunchedEffect(id) { onDismiss() }
        return
    }
    TollySheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(text.first), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(text.second), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)) {
                Text(stringResource(R.string.notice_dismiss))
            }
        }
    }
}
