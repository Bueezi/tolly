package be.tolly.core.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.R
import be.tolly.core.data.htmlToPlainText
import be.tolly.core.model.Announcement
import javax.inject.Inject
import javax.inject.Singleton

/** Posts one notification per new announcement, grouped together, each opening that announcement. */
@Singleton
class NewsNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    val canNotify: Boolean
        get() = manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun notify(announcements: List<Announcement>) {
        if (announcements.isEmpty() || !canNotify) return
        ensureChannel()
        for (announcement in announcements) {
            val preview = htmlToPlainText(announcement.bodyHtml, PREVIEW_LENGTH)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_news)
                .setContentTitle(announcement.title)
                .setContentText(announcement.courseName)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview).setSummaryText(announcement.courseName))
                .setWhen(announcement.created)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(openIntent(announcement.id))
                .build()
            notifySafely(announcement.id.hashCode(), notification)
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_news)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_news_summary, announcements.size, announcements.size))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(null))
            .build()
        notifySafely(SUMMARY_ID, summary)
    }

    /** Removes the announcement notifications still showing, after signing out. */
    fun clearAll() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.activeNotifications.filter { it.notification.group == GROUP }.forEach { manager.cancel(it.tag, it.id) }
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and now.
        }
    }

    /** Opens the app on the News tab, on [announcementId] when given. */
    private fun openIntent(announcementId: String?): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_NEWS, true)
            .apply { if (announcementId != null) putExtra(EXTRA_ANNOUNCEMENT_ID, announcementId) }
        return PendingIntent.getActivity(
            context,
            announcementId?.hashCode() ?: SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_news), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notification_channel_news_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** Intent extras MainActivity reads to open the feed (and an announcement). */
        const val EXTRA_OPEN_NEWS = "be.tolly.extra.OPEN_NEWS"
        const val EXTRA_ANNOUNCEMENT_ID = "be.tolly.extra.ANNOUNCEMENT_ID"

        private const val CHANNEL_ID = "news"
        private const val GROUP = "be.tolly.NEWS"
        private const val SUMMARY_ID = 1
        private const val PREVIEW_LENGTH = 400
    }
}
