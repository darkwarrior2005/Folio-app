package eu.kanade.tachiyomi.source.model

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SChapterImpl) return false
        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()
}
