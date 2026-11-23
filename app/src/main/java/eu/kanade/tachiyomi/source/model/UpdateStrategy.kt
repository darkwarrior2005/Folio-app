package eu.kanade.tachiyomi.source.model

/**
 * How a manga should be treated by library updates.
 *
 * @since extensions-lib 1.4
 */
@Suppress("UNUSED")
enum class UpdateStrategy {
    /** Included in library updates unless excluded by other restrictions. */
    ALWAYS_UPDATE,

    /** Skipped by library updates; for series already known to be finished. */
    ONLY_FETCH_ONCE,
}
