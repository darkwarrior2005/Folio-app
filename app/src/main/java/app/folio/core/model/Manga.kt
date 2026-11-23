package app.folio.core.model

/**
 * Where a chapter download has got to. Only [DOWNLOADED] means the pages are on disk.
 */
enum class MangaDownloadStatus {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

/**
 * How the manga library is sorted.
 */
enum class MangaSort {
    TITLE,
    LAST_READ,
    DATE_ADDED,
    UNREAD_COUNT,
    LAST_UPDATED,
}

/**
 * Which manga the library shows.
 */
enum class MangaFilter {
    ALL,
    UNREAD,
    DOWNLOADED,
    STARTED,
}
