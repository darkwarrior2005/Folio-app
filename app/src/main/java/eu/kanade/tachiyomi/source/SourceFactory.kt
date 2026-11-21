package eu.kanade.tachiyomi.source

/** An extension that exposes more than one [Source] implements this instead of [Source] itself. */
interface SourceFactory {

    fun createSources(): List<Source>
}
