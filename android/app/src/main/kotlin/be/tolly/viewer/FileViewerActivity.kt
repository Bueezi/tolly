package be.tolly.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import be.tolly.TollyActivity
import be.tolly.core.designsystem.R

/**
 * Tolly's own viewer for a downloaded file (a PDF, a picture or a text file), for users who'd
 * rather not leave the app; started by core/ui when files open in Tolly. Its intent carries the
 * file as a content URI and its type, like the `ACTION_VIEW` intent another app would get.
 */
@AndroidEntryPoint
class FileViewerActivity : TollyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        val type = intent.type
        setThemedContent {
            FileViewerScreen(
                uri = uri,
                mimeType = type,
                name = uri.lastPathSegment.orEmpty(),
                onClose = ::finish,
                onOpenExternally = {
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        startActivity(Intent.createChooser(view, uri.lastPathSegment))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}
