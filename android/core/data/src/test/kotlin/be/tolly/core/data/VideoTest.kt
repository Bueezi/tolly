package be.tolly.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTest {
    @Test
    fun findsLivestreamPinsInLinksAndHtml() {
        val html = """<p>Live: <a href="https://livestream.kuleuven.be/?pin=123456">hier</a>,
            ook https://livestream.kuleuven.be/?lang=nl&amp;pin=654321 en nog eens ?pin=123456</p>"""
        assertEquals(listOf("123456", "654321"), VideoRepository.pins(html))
        assertEquals(emptyList<String>(), VideoRepository.pins("https://www.kuleuven.be/?pin=111111"))
    }

    @Test
    fun findsEmbeddedKalturaRecordings() {
        val tool = "https://kaltura-kaf.edu.kuleuven.cloud/browseandembed/index/media/entryid/1_rd9aw2hw/showDescription/false/playerSize/960x540/"
        val html = """<iframe src="https://kaltura-kaf.edu.kuleuven.cloud/browseandembed/index/media/entryid/0_ab12CD34/x"></iframe>"""
        assertEquals(listOf("1_rd9aw2hw"), VideoRepository.kalturaEntries(tool))
        assertEquals(listOf("0_ab12CD34"), VideoRepository.kalturaEntries(html))
        assertEquals(emptyList<String>(), VideoRepository.kalturaEntries("https://example.org/entryid/1_abc"))
    }

    @Test
    fun ordersRecordingsByTheirModuleAndNumbersThoseThatShareAPage() {
        val arranged = VideoRepository.arrange(
            listOf(
                VideoRepository.Found("0_a", "Reactiesnelheid", "Kennisclips", modified = 10),
                VideoRepository.Found("0_b", "Wis I week 5", "Week 5: Hoorcollege", modified = 30),
                VideoRepository.Found("0_c", "Oplossing", "Week 4: Oefeningen", modified = 20),
                VideoRepository.Found("0_d", "Oplossing", "Week 4: Oefeningen", modified = 20),
            ),
        )
        assertEquals(listOf("Wis I week 5", "Oplossing · 1", "Oplossing · 2", "Reactiesnelheid"), arranged.map { it.title })
        assertEquals("Week 5: Hoorcollege", arranged.first().section)
    }

    @Test
    fun readsTheLivestreamAnswer() {
        val live = VideoRepository.parseLive("""{"streamUrl":"https://example.org/live/a.m3u8","roomName":"Aula A"}""")
        assertEquals("https://example.org/live/a.m3u8", live?.streamUrl)
        assertEquals("Aula A", live?.roomName)
        assertNull(VideoRepository.parseLive("""{"status":"NOT_FOUND"}"""))
    }
}
