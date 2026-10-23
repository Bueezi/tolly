package be.tolly.video

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps a video's sound playing while Tolly is in the background or the screen is off, for
 * listening to a livestream on the way to class. [PlayerActivity] owns the player and its
 * [MediaSession]; this service only holds on to that session, which shows the media notification
 * (with play/pause on the lock screen) and keeps the process in the foreground while it plays.
 */
class PlaybackService : MediaSessionService() {
    override fun onCreate() {
        super.onCreate()
        current?.let(::addSession)
    }

    /**
     * A video opened right after another was closed can reach the old service before it is gone,
     * so [onCreate] doesn't run again: swap the released session for the new one here.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val session = current
        sessions.filter { it !== session }.forEach(::removeSession)
        if (session != null && !isSessionAdded(session)) addSession(session)
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = current

    /** Stops with the app's task when nothing is playing; a playing video goes on. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = current?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    companion object {
        /** The playing video's session, from [PlayerActivity]; null when no video is open. */
        @Volatile
        private var current: MediaSession? = null

        fun start(context: Context, session: MediaSession) {
            current = session
            context.startService(Intent(context, PlaybackService::class.java))
        }

        fun stop(context: Context, session: MediaSession) {
            if (current === session) current = null
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
