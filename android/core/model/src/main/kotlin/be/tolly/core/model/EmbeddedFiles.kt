package be.tolly.core.model

/**
 * Extracts files embedded in Ultra document HTML. Ultra marks them as
 * `<a data-bbfile="{&quot;displayName&quot;:…}" href="https://…/bbcswebdav/…">`.
 */
object EmbeddedFiles {
    private val anchorTag = Regex("""<a\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val bbFileAttr = Regex("""data-bbfile\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val hrefAttr = Regex("""\bhref\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)

    fun parse(html: String?): List<EmbeddedFile> {
        if (html.isNullOrBlank() || !html.contains("data-bbfile")) return emptyList()
        return anchorTag.findAll(html).mapNotNull { fileOf(it.value) }.distinctBy { it.url.substringBefore('?') }.toList()
    }

    /**
     * [html] with every embedded file's link, the tag and its text, replaced by what [button]
     * makes of that file, so the text shows the file itself where the author linked it.
     */
    fun replaceLinks(html: String, button: (EmbeddedFile) -> String): String {
        if (!html.contains("data-bbfile")) return html
        return fileAnchor.replace(html) { anchor ->
            val tag = anchorTag.find(anchor.value)?.value
            tag?.let(::fileOf)?.let(button) ?: anchor.value
        }
    }

    /** The file an `<a data-bbfile=…>` [tag] links to; null for any other link. */
    private fun fileOf(tag: String): EmbeddedFile? {
        val json = bbFileAttr.find(tag)?.groupValues?.get(2)?.let(::decodeEntities) ?: return null
        val href = hrefAttr.find(tag)?.groupValues?.get(2)?.let(::decodeEntities)
        val url = href?.takeIf { it.startsWith("http") }
            ?: jsonString(json, "resourceUrl")
            ?: jsonString(json, "viewerUrl")
            ?: return null
        val name = jsonString(json, "displayName") ?: jsonString(json, "linkName") ?: return null
        return EmbeddedFile(displayName = name, mimeType = jsonString(json, "mimeType"), url = url)
    }

    /** [text] safe to put in HTML, as text or in a quoted attribute. */
    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /**
     * Whether [html] has anything to read besides its embedded file links: text, images, tables
     * or media. Pages that only hold files are better shown as a list of those files.
     */
    fun hasOtherContent(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        val rest = html.replace(fileAnchor, " ")
        if (media.containsMatchIn(rest)) return true
        return rest.replace(tag, " ").replace(entity, " ").isNotBlank()
    }

    /** Whether [file] is a web page that can be shown in place. */
    fun isHtml(file: EmbeddedFile): Boolean =
        file.mimeType?.startsWith("text/html", ignoreCase = true) == true ||
            file.displayName.substringAfterLast('.', "").lowercase() in setOf("html", "htm")

    /**
     * The part of an HTML file worth showing inside the app: its body and style sheets, without
     * scripts, frames, forms or anything that loads other pages. The result goes inside our own
     * page, which then sets the colours and fonts.
     */
    fun previewBody(html: String): String {
        val styles = styleBlock.findAll(html).joinToString("\n") { it.value }
        val body = bodyContent.find(html)?.groupValues?.get(1) ?: html
        val cleaned = unsafeBlocks.fold(body) { text, block -> text.replace(block, "") }
            .replace(unsafeTags, "")
            .replace(eventHandlers, "")
        return styles + cleaned
    }

    private val fileAnchor = Regex("""<a\b[^>]*data-bbfile[^>]*>.*?</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val media = Regex("""<(img|iframe|video|audio|table|object|embed|svg)\b""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]*>""")
    private val entity = Regex("""&(nbsp|#160|#xa0|zwnj|zwj|#8203);""", RegexOption.IGNORE_CASE)
    private val styleBlock = Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val bodyContent = Regex("""<body\b[^>]*>(.*)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val unsafeBlocks = listOf("script", "style", "iframe", "object", "form", "noscript", "template", "head").map { name ->
        Regex("""<$name\b[^>]*>.*?</$name\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
    private val unsafeTags = Regex("""</?(script|iframe|object|embed|form|input|button|meta|link|base|html|body|head)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val eventHandlers = Regex("""\s+on\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE)

    private fun jsonString(json: String, key: String): String? {
        val match = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json) ?: return null
        return match.groupValues[1]
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .let(::decodeEntities)
            .ifBlank { null }
    }

    internal fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
