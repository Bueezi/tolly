package be.tolly.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class KalturaGalleryTest {
    @Test
    fun readsGalleryItems() {
        val json = """[
            {"href":"/media/t/1_eb824w2k/415229582","title":"Les 4","thumb":"https://cfvod.kaltura.com/p/2375821/sp/237582100/thumbnail/entry_id/1_eb824w2k/width/478","duration":"01:48:57"},
            {"href":"/media/t/1_eb824w2k/415229582","title":"Les 4 (again)","thumb":"","duration":""},
            {"href":"","title":"No link","thumb":"","duration":""}
        ]"""
        val entries = KalturaGallery.parse(json)!!
        assertEquals(1, entries.size)
        assertEquals("1_eb824w2k", entries[0].entryId)
        assertEquals("2375821", entries[0].partnerId)
        assertEquals("01:48:57", entries[0].duration)
        assertEquals(
            "https://cdnapisec.kaltura.com/p/2375821/sp/237582100/playManifest/entryId/1_eb824w2k/format/applehttp/protocol/https/a.m3u8",
            KalturaGallery.playbackUrl(entries[0]),
        )
    }
}
