package be.tolly.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentMarkTest {
    @Test
    fun aLoneFileNeedsNineCharacters() {
        assertEquals("tolly-log", attachmentMark("tolly-log-20261030-120000.txt", listOf("tolly-log-20261030-120000.txt")))
    }

    /** Screenshots all start alike: each mark has to tell its file from the others. */
    @Test
    fun screenshotsAreToldApart() {
        val names = listOf("Screenshot_20261030_120001.png", "Screenshot_20261030_120502.png")
        assertEquals("Screenshot_20261030_1200", attachmentMark(names[0], names))
        assertEquals("Screenshot_20261030_1205", attachmentMark(names[1], names))
    }

    @Test
    fun aShortNameIsWhole() {
        assertEquals("a.png", attachmentMark("a.png", listOf("a.png", "b.png")))
    }
}
