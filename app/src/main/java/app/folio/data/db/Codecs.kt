package app.folio.data.db

import app.folio.core.model.BookLocation
import kotlinx.serialization.json.Json

object LocationCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(location: BookLocation): String = json.encodeToString(location)

    fun decode(raw: String?): BookLocation? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<BookLocation>(raw)
        } catch (e: Exception) {
            null
        }
    }
}
