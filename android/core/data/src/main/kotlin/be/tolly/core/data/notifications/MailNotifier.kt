package be.tolly.core.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user that KU Leuven mail arrived. Tolly only knows the portal's unread count, not who
 * sent what, so it is one notification that says how many are new and opens the Mail tab; a newer
 * one replaces it.
 */
@Singleton
class MailNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val news: NewsNotifier,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** [fresh] mails arrived since the last check; [unread] are unread in all. */
    fun notify(fresh: Int, unread: Int) {
        if (fresh <= 0 || !news.canNotify) return
        ensureChannel()
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_MAIL, true)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mail)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_mail_new, fresh, fresh))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_mail_unread, unread, unread))
            .setNumber(unread)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and now.
        }
    }

    /** Removes the notification: the mail was looked at in the app, or the user signed out or turned these off. */
    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_mail), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notification_channel_mail_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** The intent extra MainActivity reads to open the Mail tab. */
        const val EXTRA_OPEN_MAIL = "be.tolly.extra.OPEN_MAIL"

        private const val CHANNEL_ID = "mail"
        private const val NOTIFICATION_ID = 2
    }
}
