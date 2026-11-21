package app.folio.manga

import app.folio.core.model.Manga
import app.folio.core.model.MangaChapter
import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaEntity
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

fun MangaEntity.toDomain(): Manga = Manga(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
    status = status,
    thumbnailUrl = thumbnailUrl,
    favorite = favorite,
    initialized = initialized,
    dateAdded = dateAdded,
    lastUpdate = lastUpdate,
)

fun MangaEntity.applySManga(source: SManga): MangaEntity = copy(
    title = source.title.ifBlank { title },
    artist = source.artist ?: artist,
    author = source.author ?: author,
    description = source.description ?: description,
    genre = source.genre ?: genre,
    status = source.status,
    thumbnailUrl = source.thumbnail_url ?: thumbnailUrl,
    initialized = true,
)

fun SManga.toNewEntity(sourceId: Long, now: Long): MangaEntity = MangaEntity(
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnail_url,
    initialized = initialized,
    dateAdded = now,
    lastUpdate = now,
)

fun MangaChapterEntity.toDomain(): MangaChapter = MangaChapter(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    pagesCount = pagesCount,
    chapterNumber = chapterNumber,
    dateUpload = dateUpload,
    dateFetch = dateFetch,
    sourceOrder = sourceOrder,
)

fun MangaChapter.toEntity(): MangaChapterEntity = MangaChapterEntity(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    scanlator = scanlator,
    read = read,
    bookmark = bookmark,
    lastPageRead = lastPageRead,
    pagesCount = pagesCount,
    chapterNumber = chapterNumber,
    dateUpload = dateUpload,
    dateFetch = dateFetch,
    sourceOrder = sourceOrder,
)

fun MangaChapterEntity.toSChapter(): SChapter = SChapter.create().apply {
    url = this@toSChapter.url
    name = this@toSChapter.name
    scanlator = this@toSChapter.scanlator
    chapter_number = this@toSChapter.chapterNumber
    date_upload = this@toSChapter.dateUpload
}
