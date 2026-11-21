package eu.kanade.tachiyomi.source.model

class SMangaImpl : SManga {

    override lateinit var url: String

    // Default to "" instead of lateinit: some sources return details without a title and the
    // app should not crash on them.
    override var title: String = ""

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SMangaImpl) return false
        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()
}
