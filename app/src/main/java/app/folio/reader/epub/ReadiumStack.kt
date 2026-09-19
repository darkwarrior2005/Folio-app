package app.folio.reader.epub

import android.content.Context
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import app.folio.core.model.BookLocation
import app.folio.core.model.TocEntry
import app.folio.reader.api.BookOpenError
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Readium's asset and parsing stack. The HTTP client is required by the API but never used: all
 * assets are local files, and the app has no network permission.
 */
class ReadiumStack(context: Context) {

    private val appContext = context.applicationContext
    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = appContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    suspend fun open(uriString: String): Result<Publication> {
        val url = Uri.parse(uriString).toAbsoluteUrl()
            ?: return Result.failure(EpubOpenException(BookOpenError.FileMissing))
        val asset = assetRetriever.retrieve(url)
            .getOrElse { error ->
                return Result.failure(EpubOpenException(mapRetrieveError(error.toString())))
            }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse { error ->
                asset.close()
                return Result.failure(EpubOpenException(mapOpenError(error.message)))
            }
        return Result.success(publication)
    }

    private fun mapRetrieveError(message: String): BookOpenError = when {
        message.contains("permission", true) -> BookOpenError.PermissionDenied
        message.contains("not found", true) -> BookOpenError.FileMissing
        else -> BookOpenError.Corrupted(message)
    }

    private fun mapOpenError(message: String): BookOpenError = when {
        message.contains("not supported", true) -> BookOpenError.Unsupported
        message.contains("protect", true) || message.contains("drm", true) -> BookOpenError.Unsupported
        else -> BookOpenError.Corrupted(message)
    }
}

class EpubOpenException(val error: BookOpenError) : Exception(error.toString())

/**
 * Readium's navigator fragment can only be built by its own factory. After process death the
 * fragment manager may try to restore it before we install that factory, so this proxy stands in
 * and the reader replaces it with a fresh navigator.
 */
object ReaderFragmentFactory : FragmentFactory() {

    @Volatile
    var delegate: FragmentFactory? = null

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        delegate?.let { factory ->
            runCatching { return factory.instantiate(classLoader, className) }
        }
        return runCatching { super.instantiate(classLoader, className) }.getOrElse { Fragment() }
    }
}

// ---- Locator conversion --------------------------------------------------------

fun Locator.toBookLocation(): BookLocation = BookLocation(
    page = locations.position,
    offset = locations.progression ?: 0.0,
    href = href.toString(),
    totalProgression = locations.totalProgression ?: 0.0,
    readiumLocator = toJSON().toString(),
    label = title,
)

fun BookLocation.toReadiumLocator(): Locator? {
    val raw = readiumLocator ?: return null
    return try {
        Locator.fromJSON(JSONObject(raw))
    } catch (e: Exception) {
        null
    }
}

fun Publication.tocEntries(): List<TocEntry> {
    val out = mutableListOf<TocEntry>()
    fun walk(links: List<Link>, level: Int) {
        links.forEach { link ->
            val locator = locatorForLink(link)
            if (locator != null) {
                out += TocEntry(
                    title = link.title?.takeIf { it.isNotBlank() } ?: "—",
                    location = locator.toBookLocation(),
                    level = level,
                )
            }
            if (link.children.isNotEmpty()) walk(link.children, level + 1)
        }
    }
    walk(tableOfContents.ifEmpty { readingOrder }, 0)
    return out
}

private fun Publication.locatorForLink(link: Link): Locator? = locatorFromLink(link)
