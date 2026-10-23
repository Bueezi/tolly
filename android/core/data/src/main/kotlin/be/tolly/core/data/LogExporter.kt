package be.tolly.core.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import be.tolly.core.data.download.fileProviderUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundles this app's recent log output and crash reports from earlier runs into a text file the
 * user can share for a bug report. Query strings are stripped from URLs, since signed file links
 * and SSO redirects carry tokens there.
 */
@Singleton
class LogExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val crashDir: File get() = File(context.filesDir, "crashes")
    private val exportDir: File get() = File(context.cacheDir, "logs")

    /** Writes uncaught exceptions to a file so the next export still has them after the restart. */
    fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                crashDir.mkdirs()
                File(crashDir, "crash-${stamp(System.currentTimeMillis())}.txt")
                    .writeText("Thread: ${thread.name}\n${error.stackTraceToString()}")
                crashDir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_CRASHES)?.forEach { it.delete() }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Creates the log file and returns a share intent for it. */
    suspend fun export(): Intent {
        val (uri, name) = writeLog()
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * An e-mail to the developer with [subject] and [body], and the log attached when
     * [attachLogs]. Mail apps ignore attachments on a `mailto:` intent, so the mail is a send
     * intent aimed at the apps that handle `mailto:` (a chooser among them if there are several);
     * that way it doesn't end up in a chat by mistake. Without a mail app, [EXTRA_FALLBACK] holds
     * the share sheet for the same mail.
     */
    suspend fun feedbackEmail(to: String, subject: String, body: String, attachLogs: Boolean): Intent {
        val text = body.trimEnd() + "\n\n--\n" + header().substringBeforeLast('\n')
        val log = if (attachLogs) writeLog().first else null
        fun send(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            if (log != null) {
                putExtra(Intent.EXTRA_STREAM, log)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // The read grant travels with the ClipData.
                clipData = ClipData.newRawUri(null, log)
            }
        }
        val mailApps = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .map { it.activityInfo.packageName }
            .distinct()
        val share = Intent.createChooser(send(), subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val toMailApps = mailApps.map { send().setPackage(it) }
        return when (toMailApps.size) {
            0 -> share
            1 -> toMailApps.single().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> Intent.createChooser(toMailApps.first(), subject)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, toMailApps.drop(1).toTypedArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.putExtra(EXTRA_FALLBACK, share)
    }

    /**
     * A feedback mail written in Outlook on the web: [body] with the app and device under it, and
     * with [attachLogs] the log as a file of its own (its name and text), as a mail app gets it.
     */
    suspend fun feedbackMail(body: String, attachLogs: Boolean): FeedbackMail = withContext(Dispatchers.IO) {
        FeedbackMail(
            text = body.trimEnd() + "\n\n--\n" + header().substringBeforeLast('\n'),
            logName = if (attachLogs) "tolly-log-${stamp(System.currentTimeMillis())}.txt" else null,
            log = if (attachLogs) logText() else null,
        )
    }

    /** Writes the log file and returns its shareable uri and name. */
    private suspend fun writeLog(): Pair<Uri, String> = withContext(Dispatchers.IO) {
        exportDir.mkdirs()
        exportDir.listFiles()?.forEach { it.delete() }
        val file = File(exportDir, "tolly-log-${stamp(System.currentTimeMillis())}.txt")
        file.writeText(logText())
        context.fileProviderUri(file) to file.name
    }

    /** The log: the app and device, the saved crashes, and the app's log lines since it started. */
    private fun logText(): String = buildString {
        appendLine(header())
        crashDir.listFiles()?.sortedBy { it.name }?.forEach { crash ->
            appendLine()
            appendLine("== ${crash.name} ==")
            appendLine(redact(crash.readText()))
        }
        appendLine()
        appendLine("== Log since the app started ==")
        appendLine(redact(readLogcat()))
    }

    private fun header(): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return buildString {
            appendLine("Tolly ${version.orEmpty()} (${context.packageName})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            append("Exported ${Date()}")
        }
    }

    /** Apps may read their own process's log lines without any permission. */
    private fun readLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-t", MAX_LOG_LINES.toString(), "-v", "threadtime", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }.getOrElse { "Could not read the log: $it" }

    private fun stamp(millis: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(millis))

    companion object {
        /** An intent to start instead when no app takes the intent that carries it. */
        const val EXTRA_FALLBACK = "be.tolly.extra.FALLBACK"

        internal const val MAX_LOG_LINES = 5_000
        internal const val MAX_CRASHES = 5

        private val urlQuery = Regex("""(https?://[^\s?#"']+)\?[^\s"']*""")

        internal fun redact(text: String): String = urlQuery.replace(text) { "${it.groupValues[1]}?…" }
    }
}

/** A feedback mail's [text], and the log to attach ([logName], [log]) if the user left it on. */
class FeedbackMail(val text: String, val logName: String?, val log: String?)
