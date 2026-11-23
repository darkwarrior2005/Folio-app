package eu.kanade.tachiyomi.source

/**
 * Lets one extension provide several sources.
 */
interface SourceFactory {
    fun createSources(): List<Source>
}
