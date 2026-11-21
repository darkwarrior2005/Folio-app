package app.folio.manga

import app.folio.data.settings.SettingsRepository

/**
 * The one check every manga network path makes. While `MangaSettings.sourcesEnabled` is off (the
 * default), nothing manga-related loads an extension, connects, or even constructs a client.
 */
class MangaAccess(private val settings: SettingsRepository) {

    suspend fun isEnabled(): Boolean = settings.current().manga.sourcesEnabled

    suspend fun requireEnabled() {
        if (!isEnabled()) throw MangaSourcesDisabledException()
    }
}

class MangaSourcesDisabledException : Exception("Manga sources are turned off")
