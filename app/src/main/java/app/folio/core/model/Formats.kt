package app.folio.core.model

/** Which reader implementation renders a format. */
enum class ReaderFamily { EPUB, PDF, COMIC, TEXT }

enum class BookFormat(
    val family: ReaderFamily,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val label: String,
) {
    EPUB(ReaderFamily.EPUB, setOf("epub", "epub3"), setOf("application/epub+zip"), "EPUB"),
    PDF(ReaderFamily.PDF, setOf("pdf"), setOf("application/pdf"), "PDF"),
    CBZ(ReaderFamily.COMIC, setOf("cbz"), setOf("application/vnd.comicbook+zip", "application/x-cbz"), "CBZ"),
    CBR(ReaderFamily.COMIC, setOf("cbr"), setOf("application/vnd.comicbook-rar", "application/x-cbr"), "CBR"),
    IMAGE_FOLDER(ReaderFamily.COMIC, emptySet(), emptySet(), "Images"),
    IMAGE(
        ReaderFamily.COMIC,
        setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif"),
        setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/avif", "image/heic", "image/heif"),
        "Image",
    ),
    TXT(ReaderFamily.TEXT, setOf("txt", "text"), setOf("text/plain"), "TXT"),
    MARKDOWN(ReaderFamily.TEXT, setOf("md", "markdown", "mdown", "mkd"), setOf("text/markdown", "text/x-markdown"), "Markdown"),
    HTML(ReaderFamily.TEXT, setOf("html", "htm", "xhtml"), setOf("text/html", "application/xhtml+xml"), "HTML");

    val isReflowable: Boolean get() = family == ReaderFamily.EPUB || family == ReaderFamily.TEXT

    companion object {
        fun fromExtension(ext: String?): BookFormat? {
            val e = ext?.lowercase()?.trim() ?: return null
            return entries.firstOrNull { e in it.extensions }
        }

        fun fromMime(mime: String?): BookFormat? {
            val m = mime?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return entries.firstOrNull { m in it.mimeTypes }
        }

        fun isImageExtension(ext: String?): Boolean = ext?.lowercase() in IMAGE.extensions

        /** Extensions accepted when scanning a folder. */
        val importableExtensions: Set<String> =
            entries.flatMap { it.extensions }.toSet()
    }
}

enum class StorageMode {
    /** File stays where the user keeps it; we hold a persisted URI permission. */
    LINKED,

    /** File was copied into app storage (shared / "open with" files without persistable access). */
    COPIED,
}

enum class IndexState { PENDING, INDEXING, DONE, FAILED, UNSUPPORTED }
