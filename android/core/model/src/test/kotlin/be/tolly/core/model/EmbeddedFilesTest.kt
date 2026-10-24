package be.tolly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedFilesTest {
    private val html = """
        <p>Intro</p>
        <p><a href="https://ultra.example/bbcswebdav/pid-1-dt-content-rid-2_1/xid-2_1?a=1&amp;b=2"
              data-bbfile="{&quot;linkName&quot;:&quot;notes.pdf&quot;,&quot;displayName&quot;:&quot;Course notes.pdf&quot;,&quot;mimeType&quot;:&quot;application/pdf&quot;,&quot;render&quot;:&quot;inline&quot;}">Course notes.pdf</a></p>
        <a data-bbfile="{&quot;linkName&quot;:&quot;slides.pptx&quot;,&quot;mimeType&quot;:&quot;application/vnd.ms-powerpoint&quot;,&quot;resourceUrl&quot;:&quot;https://ultra.example/bbcswebdav/xid-3_1?x=1&amp;y=2&quot;}" href="#">x</a>
        <a href="https://example.org">plain link</a>
    """.trimIndent()

    @Test
    fun extractsFilesWithNamesTypesAndUrls() {
        val files = EmbeddedFiles.parse(html)
        assertEquals(2, files.size)
        assertEquals("Course notes.pdf", files[0].displayName)
        assertEquals("application/pdf", files[0].mimeType)
        assertEquals("https://ultra.example/bbcswebdav/pid-1-dt-content-rid-2_1/xid-2_1?a=1&b=2", files[0].url)
        assertEquals("slides.pptx", files[1].displayName)
        assertEquals("https://ultra.example/bbcswebdav/xid-3_1?x=1&y=2", files[1].url)
    }

    @Test
    fun replacesFileLinksAndTheirTextButNotOtherLinks() {
        val replaced = EmbeddedFiles.replaceLinks(html) { "[${it.displayName}]" }
        assertTrue(replaced.contains("<p>[Course notes.pdf]</p>"))
        assertTrue(replaced.contains("[slides.pptx]"))
        assertFalse(replaced.contains(">Course notes.pdf</a>"))
        assertTrue(replaced.contains("""<a href="https://example.org">plain link</a>"""))
    }

    @Test
    fun escapesTextForHtml() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &#39;e&#39;", EmbeddedFiles.escapeHtml("a & b <c> \"d\" 'e'"))
    }

    @Test
    fun ignoresHtmlWithoutFiles() {
        assertTrue(EmbeddedFiles.parse("<p>No files</p>").isEmpty())
        assertTrue(EmbeddedFiles.parse(null).isEmpty())
    }

    @Test
    fun tellsFileOnlyPagesFromPagesWithText() {
        val onlyFiles = """
            <p><a href="https://ultra.example/bbcswebdav/xid-1_1" data-bbfile="{&quot;displayName&quot;:&quot;opgave.pdf&quot;}">opgave.pdf</a></p>
            <p>&nbsp;</p>
            <div><a data-bbfile="{&quot;displayName&quot;:&quot;checker.py&quot;}" href="https://ultra.example/bbcswebdav/xid-2_1">checker.py</a></div>
        """.trimIndent()
        assertFalse(EmbeddedFiles.hasOtherContent(onlyFiles))
        assertTrue(EmbeddedFiles.hasOtherContent(onlyFiles + "<p>Deadline: Friday</p>"))
        assertTrue(EmbeddedFiles.hasOtherContent(onlyFiles + "<img src=\"x.png\">"))
        assertFalse(EmbeddedFiles.hasOtherContent(null))
    }

    @Test
    fun recognisesHtmlFiles() {
        assertTrue(EmbeddedFiles.isHtml(EmbeddedFile("UNSAFE_HTML_2026.html", null, "u")))
        assertTrue(EmbeddedFiles.isHtml(EmbeddedFile("overzicht", "text/html", "u")))
        assertFalse(EmbeddedFiles.isHtml(EmbeddedFile("slides.pdf", "application/pdf", "u")))
    }

    @Test
    fun previewKeepsBodyAndStylesButNoScripts() {
        val file = """
            <html><head><title>T</title><style>td { padding: 4px; }</style><script>alert(1)</script></head>
            <body onload="evil()"><h1>Lessen</h1><script>steal()</script><p onclick="x()">Week 1</p><iframe src="x"></iframe></body></html>
        """.trimIndent()
        val preview = EmbeddedFiles.previewBody(file)
        assertTrue(preview.contains("<style>td { padding: 4px; }</style>"))
        assertTrue(preview.contains("<h1>Lessen</h1>"))
        assertTrue(preview.contains("Week 1"))
        assertFalse(preview.contains("script", ignoreCase = true))
        assertFalse(preview.contains("onclick"))
        assertFalse(preview.contains("iframe"))
        assertFalse(preview.contains("<title>"))
    }
}
