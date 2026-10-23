package be.tolly.core.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import be.tolly.core.designsystem.R

/**
 * Returns an action that turns notifications on: it asks for the notification permission first
 * when Android requires one (13+), then calls [onResult] with whether notifications may be posted.
 * Before Android 13 there is nothing to ask, but notifications can be off for the whole app; then
 * Tolly's notification settings open, so the switch doesn't seem to do nothing.
 */
@Composable
fun rememberEnableNotifications(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.notifications_blocked, Toast.LENGTH_LONG).show()
        currentOnResult(granted)
    }
    return {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                Toast.makeText(context, R.string.notifications_off_for_app, Toast.LENGTH_LONG).show()
                val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                runCatching { context.startActivity(settings) }
                currentOnResult(true)
            }
            else -> currentOnResult(true)
        }
    }
}
