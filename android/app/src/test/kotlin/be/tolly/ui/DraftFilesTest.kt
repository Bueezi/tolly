package be.tolly.ui

import be.tolly.core.data.MailAttachment
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.random.Random

class DraftFilesTest {
    /** Read piece by piece, as the page does, a file comes out whole. */
    @Test
    fun piecesMakeTheWholeFile() {
        val bytes = Random(1).nextBytes(DraftFiles.CHUNK * 2 + 123)
        val file = File.createTempFile("draft", ".bin").apply { writeBytes(bytes); deleteOnExit() }
        val files = DraftFiles()
        val key = files.offer(listOf(MailAttachment("a.bin", "application/octet-stream", file.path))).single()
        val size = files.size(key)
        assertEquals(bytes.size, size)
        val read = ByteArrayOutputStream()
        for (offset in 0 until size step DraftFiles.CHUNK) read.write(Base64.getDecoder().decode(files.read(key, offset)))
        assertArrayEquals(bytes, read.toByteArray())
    }

    /** Only the latest draft's files can be read. */
    @Test
    fun earlierKeysStopWorking() {
        val file = File.createTempFile("draft", ".txt").apply { writeText("log"); deleteOnExit() }
        val files = DraftFiles()
        val old = files.offer(listOf(MailAttachment("log.txt", "text/plain", file.path))).single()
        files.offer(emptyList())
        assertEquals(-1, files.size(old))
        assertEquals("", files.read(old, 0))
    }
}
