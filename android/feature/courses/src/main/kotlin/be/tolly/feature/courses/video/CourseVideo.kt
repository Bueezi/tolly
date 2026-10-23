package be.tolly.feature.courses.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import be.tolly.core.auth.GalleryEntry
import be.tolly.core.auth.KalturaGallery
import be.tolly.core.data.DataError
import be.tolly.core.data.LiveLink
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GroupDefaults
import be.tolly.core.designsystem.component.IconTile
import be.tolly.core.designsystem.component.ItemPosition
import be.tolly.core.designsystem.component.SectionHeader
import be.tolly.core.designsystem.component.TileColor
import be.tolly.core.designsystem.component.TollyListItem
import be.tolly.core.designsystem.component.TollyPullToRefresh
import be.tolly.core.designsystem.component.groupItem
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.ui.openInBrowser
import be.tolly.core.ui.playVideo
import be.tolly.core.ui.withHaptic

/**
 * A course's Video tab: its livestreams (the livestream links anywhere in its content) and its
 * lesson recordings (its Kaltura gallery, "Lesopnames"). Both play in the app's own player, which
 * can also open them in a browser. Nothing is looked up until the tab is first shown ([active]).
 */
@Composable
internal fun CourseVideo(courseId: String, active: Boolean, snackbarHostState: SnackbarHostState) {
    val viewModel = hiltViewModel<CourseVideoViewModel, CourseVideoViewModel.Factory>(key = "video:$courseId") { it.create(courseId) }
    LaunchedEffect(active) { if (active) viewModel.load() }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notLive = stringResource(R.string.video_not_live)
    val liveFailed = stringResource(R.string.video_live_failed)
    LaunchedEffect(viewModel) {
        viewModel.liveResult.collect { result ->
            when (result) {
                is LiveResult.Play -> context.playVideo(
                    url = result.stream.streamUrl,
                    title = result.stream.roomName ?: result.link.title,
                    webUrl = liveWebUrl(result.link.pin),
                    live = true,
                )
                is LiveResult.NotLive -> snackbarHostState.showSnackbar(notLive)
                LiveResult.Failed -> snackbarHostState.showSnackbar(liveFailed)
            }
        }
    }
    TollyPullToRefresh(
        onRefresh = withHaptic(HapticFeedbackType.GestureThresholdActivate, viewModel::refresh),
        topPadding = 0.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomBarInset.current + 16.dp),
        ) {
            item(key = "live-header") { SectionHeader(stringResource(R.string.video_live)) }
            videoSection(
                state = live,
                key = "live",
                empty = R.string.video_live_none,
                loading = R.string.video_live_loading,
                itemKey = { it.pin },
            ) { link, position -> LiveRow(link, position, onClick = { viewModel.openLive(link) }) }

            val play: (GalleryEntry) -> Unit = { entry ->
                context.playVideo(KalturaGallery.playbackUrl(entry), entry.title, webUrl = KalturaGallery.launchUrl(courseId))
            }
            val loaded = (recordings as? VideoList.Loaded)?.items.orEmpty()
            if (loaded.isEmpty()) {
                item(key = "recordings-header") { SectionHeader(stringResource(R.string.video_recordings)) }
                videoSection(
                    state = recordings,
                    key = "recordings",
                    empty = R.string.video_recordings_none,
                    loading = R.string.video_recordings_loading,
                    itemKey = { it.entryId },
                ) { entry, position -> RecordingRow(entry, position, onClick = { play(entry) }) }
            } else {
                // One group per module the recordings are in; the gallery's own come under "Recordings".
                loaded.groupBy { it.section }.forEach { (section, entries) ->
                    item(key = "recordings-header:$section") { SectionHeader(section ?: stringResource(R.string.video_recordings)) }
                    itemsIndexed(entries, key = { _, it -> "recordings:${it.entryId}" }) { index, entry ->
                        RecordingRow(entry, ItemPosition(index, entries.size), onClick = { play(entry) })
                    }
                }
            }
            item(key = "gallery") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { context.openInBrowser(KalturaGallery.launchUrl(courseId)) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.video_open_gallery), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** The rows of one section, or a line saying it is being looked up, empty or unavailable. */
private fun <T> LazyListScope.videoSection(
    state: VideoList<T>,
    key: String,
    empty: Int,
    loading: Int,
    itemKey: (T) -> Any,
    row: @Composable (T, ItemPosition) -> Unit,
) {
    when (state) {
        VideoList.Loading -> item(key = "$key-loading") { Note(stringResource(loading)) }
        is VideoList.Failed -> item(key = "$key-failed") {
            Note(stringResource(if (state.error == DataError.OFFLINE) R.string.video_offline else R.string.video_unavailable))
        }
        is VideoList.Loaded -> if (state.items.isEmpty()) {
            item(key = "$key-empty") { Note(stringResource(empty)) }
        } else {
            itemsIndexed(state.items, key = { _, it -> "$key:${itemKey(it)}" }) { index, item ->
                row(item, ItemPosition(index, state.items.size))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun LiveRow(link: LiveLink, position: ItemPosition, onClick: () -> Unit) {
    TollyListItem(
        headline = { Text(link.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = { Text(stringResource(R.string.video_live_pin, link.pin)) },
        leading = { IconTile(Icons.Outlined.LiveTv, TileColor.Red) },
        modifier = Modifier.groupItem(position, onClick = onClick, separatorStart = GroupDefaults.separatorStart(30.dp)),
    )
}

@Composable
private fun RecordingRow(entry: GalleryEntry, position: ItemPosition, onClick: () -> Unit) {
    TollyListItem(
        headline = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supporting = entry.duration?.let { { Text(it) } },
        leading = {
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                IconTile(Icons.Outlined.VideoLibrary, TileColor.Indigo)
            }
        },
        modifier = Modifier.groupItem(position, onClick = onClick, separatorStart = GroupDefaults.separatorStart(96.dp)),
    )
}

/** The livestream's page, where it also plays (and can be cast). */
private fun liveWebUrl(pin: String) = "https://livestream.kuleuven.be/?pin=$pin"
