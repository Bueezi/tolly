package be.tolly.video

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.MainActivity
import be.tolly.TollyActivity
import be.tolly.core.data.WatchProgressRepository
import be.tolly.core.designsystem.theme.TollyTheme
import be.tolly.core.model.UserPreferences
import be.tolly.core.ui.EXTRA_VIDEO_KEY
import be.tolly.core.ui.EXTRA_VIDEO_LIVE
import be.tolly.core.ui.EXTRA_VIDEO_TITLE
import be.tolly.core.ui.EXTRA_VIDEO_WEB_URL
import be.tolly.core.ui.openInBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the player shows: the stream, its name and where it is on the web; [key] names a recording
 * whose progress is kept.
 */
private data class Video(val url: String, val title: String, val webUrl: String?, val live: Boolean, val key: String?)

/**
 * Plays a lesson recording or a livestream (HLS) in the app, with Tolly's own controls in the
 * user's look ([VideoPlayer]). The bar on top closes it or opens the video's page in a browser
 * instead. In landscape the video takes the whole screen. It is always dark, whatever the theme.
 *
 * Leaving the app while a video plays shrinks it to picture-in-picture, and its sound goes on with
 * the screen off or the app in the background ([PlaybackService]), for following a livestream on
 * the way to class. The player runs in a task of its own (see the manifest), so picture-in-picture
 * takes only the video along, and opening another video replaces this one.
 *
 * A recording continues where the user left it ([WatchProgressRepository]); the position is kept
 * every few seconds while it plays, when it pauses and when the player closes.
 */
@AndroidEntryPoint
class PlayerActivity : TollyActivity() {
    @Inject lateinit var watchProgress: WatchProgressRepository

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var video by mutableStateOf<Video?>(null)
    private var inPictureInPicture by mutableStateOf(false)

    /** Some devices (Android Go, some tablets) have no picture-in-picture; asking for it there throws. */
    private val pictureInPictureSupported by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    /** Picture-in-picture just ended without the player coming back: if it stops next, it was closed. */
    private var leftPictureInPicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val first = videoOf(intent)
        if (first == null) {
            finish()
            return
        }
        val exo = ExoPlayer.Builder(this)
            // Pauses for calls and other apps' sound, and when headphones are unplugged.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            // Keeps the stream loading with the screen off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exo
        play(first, position = savedInstanceState?.getLong(POSITION) ?: 0L)
        exo.setPlaybackSpeed(savedInstanceState?.getFloat(SPEED) ?: 1f)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePictureInPicture()
                if (!isPlaying) saveProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) saveProgress()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) = updatePictureInPicture()
        })
        lifecycleScope.launch {
            while (true) {
                delay(SAVE_EVERY_MS)
                if (player?.isPlaying == true) saveProgress()
            }
        }
        // The notification brings this screen back.
        val reopen = Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(PendingIntent.getActivity(this, 0, reopen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
            .also { PlaybackService.start(this, it) }
        window.decorView.keepScreenOn = true
        followOrientation(resources.configuration)
        updatePictureInPicture()

        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(UserPreferences())
            val shown = video
            SystemBarsFollowTheme(dark = true)
            if (shown != null) TollyTheme(darkTheme = true, dynamicColor = prefs.dynamicColor, glass = prefs.glass) {
                VideoPlayer(
                    player = exo,
                    title = shown.title,
                    live = shown.live,
                    onClose = ::finish,
                    onOpenInBrowser = shown.webUrl?.let { url -> { openInBrowser(url) } },
                    pictureInPicture = inPictureInPicture,
                )
            }
        }
    }

    /** Another video was opened while this one is still around (perhaps in picture-in-picture). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = videoOf(intent) ?: return
        // A recreation (or process death) restores this video, not the first one.
        setIntent(intent)
        saveProgress()
        play(next, position = 0L)
    }

    private fun play(next: Video, position: Long) {
        val exo = player ?: return
        video = next
        val item = MediaItem.Builder()
            .setUri(next.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(next.title).build())
            .build()
        if (next.live) {
            // A livestream joins at the live edge.
            exo.setMediaItem(item)
            exo.prepare()
            exo.playWhenReady = true
            return
        }
        // A recording continues where it was: after the process was gone, from the saved state,
        // else where the user left it last time (unless they watched it to the end).
        lifecycleScope.launch {
            val from = if (position > 0 || next.key == null) position else watchProgress.get(next.key)?.takeUnless { it.finished }?.position ?: 0L
            if (video != next) return@launch
            exo.setMediaItem(item, from)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    /** Keeps how far the recording got, for opening it again and the Video tab's progress bars. */
    private fun saveProgress() {
        val exo = player ?: return
        val shown = video ?: return
        val key = shown.key ?: return
        if (shown.live || exo.currentMediaItem?.localConfiguration?.uri?.toString() != shown.url) return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        watchProgress.save(key, if (exo.playbackState == Player.STATE_ENDED) duration else exo.currentPosition, duration)
    }

    private fun videoOf(intent: Intent?): Video? {
        val url = intent?.dataString ?: return null
        return Video(
            url = url,
            title = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty(),
            webUrl = intent.getStringExtra(EXTRA_VIDEO_WEB_URL),
            live = intent.getBooleanExtra(EXTRA_VIDEO_LIVE, false),
            key = intent.getStringExtra(EXTRA_VIDEO_KEY),
        )
    }

    /** While playing, going home shrinks the video into a window (by itself from Android 12). */
    private fun updatePictureInPicture() {
        if (pictureInPictureSupported) runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    private fun pictureInPictureParams(): PictureInPictureParams {
        val size = player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) {
            // Android refuses ratios beyond 2.39:1 either way.
            Rational(size.width, size.height).takeIf { it.toFloat() in 0.42f..2.39f }
        } else {
            null
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(ratio ?: Rational(16, 9))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(player?.isPlaying == true) }
            .build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12 and later enter by themselves (setAutoEnterEnabled).
        if (pictureInPictureSupported && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player?.isPlaying == true) {
            runCatching { enterPictureInPictureMode(pictureInPictureParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        // Closing the window (its X, or dragging it away) stops the activity and ends
        // picture-in-picture, in either order; the sound would otherwise go on with nothing to show.
        // Expanding it back resumes the activity instead.
        if (!isInPictureInPictureMode) {
            if (lifecycle.currentState == Lifecycle.State.CREATED) closedFromPictureInPicture() else leftPictureInPicture = true
        }
    }

    override fun onResume() {
        super.onResume()
        leftPictureInPicture = false
    }

    // No pause in onStop: the sound goes on in the background, until the video is closed.
    override fun onStop() {
        super.onStop()
        saveProgress()
        if (leftPictureInPicture) closedFromPictureInPicture()
    }

    /**
     * The picture-in-picture window was closed: the video is done, and Tolly comes back where it
     * was (the course's video tab, say), as when the player closes with its own X, rather than
     * leaving the user on the home screen.
     */
    private fun closedFromPictureInPicture() {
        leftPictureInPicture = false
        runCatching {
            val tasks = getSystemService(ActivityManager::class.java).appTasks
            val main = tasks.firstOrNull { it.taskInfo?.baseIntent?.component?.className == MainActivity::class.java.name }
            main?.moveToFront()
        }
        finish()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        followOrientation(newConfig)
    }

    /** Hides the system bars in landscape, where the video fills the screen. */
    private fun followOrientation(config: Configuration) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let {
            outState.putLong(POSITION, it.currentPosition)
            outState.putFloat(SPEED, it.playbackParameters.speed)
        }
    }

    override fun onDestroy() {
        saveProgress()
        session?.let {
            PlaybackService.stop(this, it)
            it.release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val POSITION = "position"
        const val SPEED = "speed"
        const val SAVE_EVERY_MS = 5_000L
    }
}
