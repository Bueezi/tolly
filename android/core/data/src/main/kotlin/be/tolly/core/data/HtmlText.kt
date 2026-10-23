package be.tolly.core.data

import androidx.core.text.HtmlCompat

/**
 * An announcement's (or other HTML's) text on one line, for a preview: tags and embedded images
 * dropped, whitespace collapsed, and at most [maxLength] characters.
 */
fun htmlToPlainText(html: String, maxLength: Int): String =
    HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        .replace('￼', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
