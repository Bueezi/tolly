package be.tolly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import be.tolly.core.data.OfflineFile
import be.tolly.core.designsystem.theme.TollyTheme
import be.tolly.core.model.DownloadRequest
import be.tolly.feature.downloads.offline.OfflineActions
import be.tolly.feature.downloads.offline.OfflineContent
import be.tolly.feature.downloads.offline.OfflinePhase
import be.tolly.feature.downloads.offline.OfflineUiState

/**
 * Debug-only: the "download for offline" picker with made-up files, since a real scan needs a
 * Toledo session. `adb shell am start -n be.tolly.debug/be.tolly.OfflinePreviewActivity`
 */
class OfflinePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mb = 1_048_576L
        fun file(title: String, name: String, mime: String, folder: String, size: Long?, done: Boolean = false) = OfflineFile(
            DownloadRequest("_1_1", "Logica voor informatici", "_$title", title, name, mime, "https://example.invalid/$name", folder),
            size,
            done,
        )
        val files = listOf(
            file("Lesopname week 3", "week3.mp4", "video/mp4", "Hoorcolleges", 412 * mb),
            file("Cursustekst", "cursustekst.pdf", "application/pdf", "Cursusmateriaal", 18 * mb),
            file("Les 2 – Natuurlijke deductie", "les2.pptx", "application/vnd.ms-powerpoint", "Hoorcolleges", 9 * mb, done = true),
            file("Les 1 – Propositielogica", "les1.pdf", "application/pdf", "Hoorcolleges", 3 * mb),
            file("Oefeningen reeks 1", "reeks1.pdf", "application/pdf", "Oefenzittingen/Reeks 1", 512_000),
            file("pc-sessie1.lean", "pc-sessie1.lean", "text/plain", "Hoorcolleges", 4_096),
            file("Extra lectuur", "extra.pdf", "application/pdf", "Cursusmateriaal", null),
        )
        setContent {
            TollyTheme {
                var state by remember {
                    mutableStateOf(
                        OfflineUiState(
                            phase = OfflinePhase.Ready(files),
                            selected = files.filterNot { it.alreadyDownloaded }.map { it.request.key }.toSet(),
                            freeBytes = 300 * mb,
                            courseName = "Logica voor informatici",
                            courseCount = 1,
                        ),
                    )
                }
                OfflineContent(
                    state = state,
                    actions = OfflineActions(
                        onClose = ::finish,
                        onRetry = {},
                        onToggle = { f ->
                            val key = f.request.key
                            state = state.copy(selected = if (key in state.selected) state.selected - key else state.selected + key)
                        },
                        onSelectAll = { state = state.copy(selected = files.filterNot { it.alreadyDownloaded }.map { it.request.key }.toSet()) },
                        onSelectNone = { state = state.copy(selected = emptySet()) },
                        onHideDownloaded = { state = state.copy(hideDownloaded = it) },
                        onCopyToDownloads = { state = state.copy(copyToDownloads = it) },
                        onDownload = ::finish,
                    ),
                )
            }
        }
    }
}
