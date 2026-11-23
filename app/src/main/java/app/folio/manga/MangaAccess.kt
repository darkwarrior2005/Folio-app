package app.folio.manga

import app.folio.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Thrown when something tries to reach the network while manga sources are turned off. */
class SourcesDisabledException : IllegalStateException("Manga sources are turned off")

/**
 * The switch that keeps Folio offline until the user asks otherwise.
 *
 * Folio holds the INTERNET permission only for the manga library, so every path that would open
 * a connection — browsing a source, reading a repository index, installing an extension,
 * downloading a chapter — calls [require] first. With the setting off, nothing here reaches the
 * network, and the book, music and reader code never does at all.
 */
class MangaAccess(private val settings: SettingsRepository) {

    suspend fun enabled(): Boolean = settings.current().manga.sourcesEnabled

    suspend fun require() {
        if (!enabled()) throw SourcesDisabledException()
    }

    fun observeEnabled(): Flow<Boolean> =
        settings.settings.map { it.manga.sourcesEnabled }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        settings.update { it.copy(manga = it.manga.copy(sourcesEnabled = enabled)) }
    }
}
