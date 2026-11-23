package app.folio.manga

import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaEntity
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Turns stored rows back into the shapes a source expects.
 *
 * A source is given what it gave us — above all the URL and its memo — because that is what its
 * requests are built from. Folio's own columns, such as read state or the cached cover, are not
 * its business.
 */
fun MangaEntity.toSManga(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.author = author
    it.artist = artist
    it.description = description
    it.genre = genre
    it.status = status
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
    it.update_strategy = if (onlyFetchOnce) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
    decodeMemo(memo)?.let { stored -> it.memo = stored }
}

fun MangaChapterEntity.toSChapter(): SChapter = SChapter.create().also {
    it.url = url
    it.name = name
    it.scanlator = scanlator
    it.chapter_number = chapterNumber
    it.date_upload = dateUpload
    decodeMemo(memo)?.let { stored -> it.memo = stored }
}

/**
 * A memo as stored: JSON text, or null when the source left it empty. Reading `memo` on a model
 * from an older extension that never set it can throw, which also counts as empty.
 */
fun memoText(read: () -> JsonObject): String? =
    runCatching { read() }.getOrNull()?.takeIf { it.isNotEmpty() }?.toString()

fun decodeMemo(text: String?): JsonObject? =
    text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
