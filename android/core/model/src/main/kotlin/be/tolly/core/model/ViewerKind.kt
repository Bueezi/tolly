package be.tolly.core.model

/**
 * What Tolly's own file viewer can show: PDFs page by page, pictures, and plain text or code.
 * Everything else (Office files, archives) always opens in another app.
 */
enum class ViewerKind {
    PDF,
    IMAGE,
    TEXT,
    ;

    companion object {
        fun of(mimeType: String?, fileName: String?): ViewerKind? {
            val type = mimeType?.lowercase().orEmpty()
            val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return when {
                type == "application/pdf" || extension == "pdf" -> PDF
                type in images || extension in imageExtensions -> IMAGE
                (type.startsWith("text/") && type != "text/html") || extension in textExtensions -> TEXT
                else -> null
            }
        }

        private val images = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "image/heic", "image/heif")
        private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")
        private val textExtensions = setOf(
            "txt", "md", "csv", "tsv", "log", "json", "xml", "yaml", "yml", "toml", "ini", "tex", "bib",
            "py", "java", "kt", "c", "h", "cpp", "hpp", "cs", "js", "ts", "go", "rs", "rb", "php", "sh",
            "sql", "r", "hs", "lean", "scala", "swift", "lua", "asm", "css",
        )
    }
}
