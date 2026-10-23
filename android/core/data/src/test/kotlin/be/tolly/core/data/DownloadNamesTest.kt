package be.tolly.core.data

import be.tolly.core.data.download.DownloadFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    @Test
    fun longNamesKeepTheirExtension() {
        val name = DownloadFiles.safeName("a".repeat(200) + ".pdf")
        assertEquals(120, name.length)
        assertEquals(".pdf", name.takeLast(4))
    }

    @Test
    fun takenNamesGetANumber() {
        val taken = setOf("Slides.pdf", "Slides (2).pdf", "README")
        assertEquals("Slides.pdf", DownloadFiles.freeName("Slides.pdf") { false })
        assertEquals("Slides (3).pdf", DownloadFiles.freeName("Slides.pdf") { it in taken })
        assertEquals("README (2)", DownloadFiles.freeName("README") { it in taken })
    }
}
