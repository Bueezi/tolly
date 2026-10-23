package be.tolly.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.GlassCircleButton
import be.tolly.core.designsystem.component.TollyBackButton
import be.tolly.core.designsystem.theme.LocalChromeBackdrop
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.glassChrome
import kotlinx.coroutines.delay

/** What the controls show of the player, kept up to date by [rememberPlayerState]. */
@Stable
private class PlayerState(val player: Player) {
    var showPlay by mutableStateOf(Util.shouldShowPlayButton(player))
    var buffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    var position by mutableLongStateOf(player.currentPosition)
    var duration by mutableLongStateOf(player.duration)
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
    var failed by mutableStateOf(player.playerError != null)

    fun update() {
        showPlay = Util.shouldShowPlayButton(player)
        buffering = player.playbackState == Player.STATE_BUFFERING
        position = player.currentPosition
        duration = player.duration
        speed = player.playbackParameters.speed
        failed = player.playerError != null
    }
}

@Composable
private fun rememberPlayerState(player: Player): PlayerState {
    val state = remember(player) { PlayerState(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.update()
            override fun onPlayerError(error: PlaybackException) = state.update()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // The position moves without events; follow it while playing.
    LaunchedEffect(player, state.showPlay) {
        while (!state.showPlay) {
            state.position = player.currentPosition
            delay(250)
        }
    }
    return state
}

/**
 * The video with Tolly's own controls in the current look: Expressive buttons over a dark scrim
 * in Material, glass capsules that refract the video with the glass look. Tapping the video shows
 * or hides the controls (they hide by themselves while playing), and a double tap on its left or
 * right half goes back or forward 10 seconds, as in most video apps. In [pictureInPicture] the
 * window is too small for controls: only the video shows.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(
    player: Player,
    title: String,
    live: Boolean,
    onClose: () -> Unit,
    onOpenInBrowser: (() -> Unit)?,
    pictureInPicture: Boolean = false,
) {
    val state = rememberPlayerState(player)
    val presentation = rememberPresentationState(player)
    val haptics = LocalHapticFeedback.current
    var shown by rememberSaveable { mutableStateOf(true) }
    // Bumped on every touch of the controls, so they stay while being used.
    var touched by remember { mutableIntStateOf(0) }
    val seek: (Long) -> Unit = { by ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        player.seekTo((player.currentPosition + by).coerceIn(0, player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE))
        state.update()
        touched++
    }

    LaunchedEffect(shown, state.showPlay, touched) {
        if (shown && !state.showPlay) {
            delay(HIDE_AFTER_MS)
            shown = false
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .pointerInput(live) {
                    detectTapGestures(
                        onTap = { shown = !shown },
                        onDoubleTap = if (live) {
                            null
                        } else {
                            { offset -> seek(if (offset.x < size.width / 2) -SEEK_MS else SEEK_MS) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // A TextureView, unlike a SurfaceView, is drawn with the rest of the UI, so glass can refract it.
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
            )
            if (presentation.coverSurface) Box(Modifier.matchParentSize().background(Color.Black))
        }

        if (state.buffering && (!shown || pictureInPicture)) Buffering(Modifier.align(Alignment.Center))

        CompositionLocalProvider(LocalChromeBackdrop provides backdrop) {
            AnimatedVisibility(visible = (shown || state.failed) && !pictureInPicture, enter = fadeIn(), exit = fadeOut()) {
                Controls(
                    state = state,
                    title = title,
                    live = live,
                    onClose = onClose,
                    onOpenInBrowser = onOpenInBrowser,
                    onSeek = seek,
                    onTouch = { touched++ },
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: PlayerState,
    title: String,
    live: Boolean,
    onClose: () -> Unit,
    onOpenInBrowser: (() -> Unit)?,
    onSeek: (Long) -> Unit,
    onTouch: () -> Unit,
) {
    val glass = Tolly.glass
    Box(
        Modifier
            .fillMaxSize()
            .then(
                // Glass needs no scrim: each control carries its own frosted background.
                if (glass) {
                    Modifier
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.6f),
                            0.3f to Color.Black.copy(alpha = 0.15f),
                            0.7f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.7f),
                        ),
                    )
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TollyBackButton(onClick = onClose, close = true)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onOpenInBrowser != null) {
                ChromeIconButton(onClick = onOpenInBrowser) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.video_open_in_browser))
                }
            }
        }

        if (state.failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.video_error), color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (onOpenInBrowser != null) {
                    Button(onClick = onOpenInBrowser) { Text(stringResource(R.string.video_open_in_browser)) }
                }
            }
            return@Box
        }

        CenterButtons(
            state = state,
            live = live,
            onSeek = onSeek,
            onTouch = onTouch,
            modifier = Modifier.align(Alignment.Center),
        )

        BottomBar(
            state = state,
            live = live,
            onTouch = onTouch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
        )
    }
}

/** Back 10 s, play/pause and forward 10 s: one glass capsule, or Expressive buttons in Material. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenterButtons(state: PlayerState, live: Boolean, onSeek: (Long) -> Unit, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val player = state.player
    val playPause = {
        haptics.performHapticFeedback(if (state.showPlay) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        Util.handlePlayPauseButtonAction(player)
        state.update()
        onTouch()
    }
    val playIcon: @Composable () -> Unit = {
        when {
            state.buffering -> Buffering(Modifier.size(40.dp))
            state.showPlay -> Icon(Icons.Filled.PlayArrow, stringResource(R.string.video_play), Modifier.size(44.dp))
            else -> Icon(Icons.Filled.Pause, stringResource(R.string.video_pause), Modifier.size(44.dp))
        }
    }
    val backdrop = LocalChromeBackdrop.current
    if (Tolly.glass && backdrop != null) {
        // Icons sit on the glass itself, which is dark over a video.
        CompositionLocalProvider(LocalContentColor provides Color.White) { GlassCenterButtons(backdrop, live, onSeek, playPause, playIcon, modifier) }
        return
    }
    // Expressive: the play button is a squircle while playing and turns round when paused.
    val corner by animateDpAsState(if (state.showPlay) 48.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (!live) {
            FilledTonalIconButton(onClick = { onSeek(-SEEK_MS) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Replay10, stringResource(R.string.video_back_10), Modifier.size(32.dp))
            }
        }
        FilledIconButton(onClick = playPause, shape = RoundedCornerShape(corner), modifier = Modifier.size(96.dp)) { playIcon() }
        if (!live) {
            FilledTonalIconButton(onClick = { onSeek(SEEK_MS) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Forward10, stringResource(R.string.video_forward_10), Modifier.size(32.dp))
            }
        }
    }
}

/** The glass look's middle controls: one capsule that refracts the video. */
@Composable
private fun GlassCenterButtons(
    backdrop: Backdrop,
    live: Boolean,
    onSeek: (Long) -> Unit,
    playPause: () -> Unit,
    playIcon: @Composable () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.glassChrome(backdrop, CircleShape, refraction = 24.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!live) {
            IconButton(onClick = { onSeek(-SEEK_MS) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay10, stringResource(R.string.video_back_10), Modifier.size(32.dp))
            }
        }
        IconButton(onClick = playPause, modifier = Modifier.size(72.dp)) { playIcon() }
        if (!live) {
            IconButton(onClick = { onSeek(SEEK_MS) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Forward10, stringResource(R.string.video_forward_10), Modifier.size(32.dp))
            }
        }
    }
}

/** The time, the seek bar and the speed; for a livestream a "Live" label instead of the seek bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(state: PlayerState, live: Boolean, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val player = state.player
    val haptics = LocalHapticFeedback.current
    val glass = Tolly.glass
    val backdrop = LocalChromeBackdrop.current
    // While dragging, the bar follows the finger rather than the player.
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = state.duration.takeIf { it != C.TIME_UNSET && it > 0 }
    val shown = scrubbing?.let { (it * (duration ?: 0)).toLong() } ?: state.position
    val timeStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")

    Row(
        modifier
            .then(if (glass && backdrop != null) Modifier.glassChrome(backdrop, CircleShape) else Modifier)
            .padding(horizontal = if (glass) 16.dp else 4.dp, vertical = if (glass) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (live) {
            Box(Modifier.size(10.dp).background(Color(0xFFFF3B30), CircleShape))
            Text(stringResource(R.string.video_live), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.weight(1f))
        } else {
            Text(formatTime(shown), style = timeStyle, color = Color.White)
            Slider(
                value = scrubbing ?: if (duration != null) (state.position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = {
                    scrubbing = it
                    onTouch()
                },
                onValueChangeFinished = {
                    scrubbing?.let { fraction -> if (duration != null) player.seekTo((fraction * duration).toLong()) }
                    scrubbing = null
                    state.update()
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                },
                enabled = duration != null,
                colors = if (glass) {
                    SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    )
                } else {
                    SliderDefaults.colors()
                },
                modifier = Modifier.weight(1f),
            )
            Text(duration?.let(::formatTime) ?: "--:--", style = timeStyle, color = Color.White)
        }
        TextButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                val next = SPEEDS[(SPEEDS.indexOf(state.speed).takeIf { it >= 0 }?.plus(1) ?: 0) % SPEEDS.size]
                player.setPlaybackSpeed(next)
                state.update()
                onTouch()
            },
        ) {
            Text(formatSpeed(state.speed), style = timeStyle, color = Color.White)
        }
    }
}

/** A round button in the top bar: glass with the glass look, tonal in Material. */
@Composable
private fun ChromeIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    if (Tolly.glass) {
        GlassCircleButton(onClick = onClick, content = content)
    } else {
        // Like TollyBackButton beside it.
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Buffering(modifier: Modifier = Modifier) {
    if (Tolly.glass) {
        CircularProgressIndicator(modifier = modifier, color = Color.White)
    } else {
        LoadingIndicator(modifier = modifier)
    }
}

/** 4:05 or 1:04:05. */
internal fun formatTime(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 1×, 1.25×, 2×. */
internal fun formatSpeed(speed: Float): String = speed.toString().removeSuffix(".0") + "×"

private const val SEEK_MS = 10_000L
private const val HIDE_AFTER_MS = 3_000L
private val SPEEDS = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
