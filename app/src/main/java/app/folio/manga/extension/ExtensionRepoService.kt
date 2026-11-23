package app.folio.manga.extension

import app.folio.data.db.ExtensionRepoEntity
import app.folio.data.db.FolioDatabase
import app.folio.manga.MangaAccess
import app.folio.manga.extension.model.ContentWarning
import app.folio.manga.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Talks to the extension repositories the user added.
 *
 * Folio ships with none. A repository is a static site laid out the way the Tachiyomi and Mihon
 * ecosystem expects:
 *
 *   <base>/repo.json       the repository's own name, website and signing key
 *   <base>/index.min.json  the extensions it offers
 *   <base>/apk/<file>.apk  the apks
 *   <base>/icon/<pkg>.png  the icons
 *
 * Repositories built for Mihon 0.20.1+ instead name an `index_v2` in `repo.json` (a protobuf
 * `index.pb`, see [RepoIndexV2]) that carries full apk and icon URLs, and leave only a
 * placeholder in `index.min.json`.
 *
 * The signing key in `repo.json` is what makes adding a repository meaningful: every extension
 * from it must be signed with that key, so adding the repository is the act of trusting its
 * author, and individual extensions from it do not need approving one by one.
 *
 * Adapted from Mihon (Apache-2.0).
 */

/** The result of asking every repository for its extensions: what came back, and which repos didn't answer. */
data class AvailableExtensionsResult(
    val extensions: List<Extension.Available>,
    val failedRepoNames: List<String>,
    val totalRepos: Int,
)

class ExtensionRepoService(
    private val database: FolioDatabase,
    private val network: NetworkHelper,
    private val access: MangaAccess,
) {

    private val dao get() = database.manga()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun observeRepos(): Flow<List<ExtensionRepoEntity>> = dao.observeRepos()

    suspend fun repos(): List<ExtensionRepoEntity> = dao.repos()

    /**
     * Fetches a repository's details and stores it. Accepts the base URL or any of the index
     * URLs people copy (`index.min.json`, `index.pb`, `repo.json`).
     */
    suspend fun addRepo(url: String): Result<ExtensionRepoEntity> = withContext(Dispatchers.IO) {
        try {
            access.require()
            val base = normalizeRepoBaseUrl(url)
            val meta = fetchRepoMeta(base)

            val repo = ExtensionRepoEntity(
                baseUrl = base,
                name = meta.meta.name,
                shortName = meta.meta.shortName,
                website = meta.meta.website,
                signingKeyFingerprint = meta.meta.signingKeyFingerprint.lowercase(),
                addedAt = System.currentTimeMillis(),
            )
            dao.upsertRepo(repo)
            Result.success(repo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeRepo(baseUrl: String) {
        dao.deleteRepo(baseUrl)
    }

    /**
     * Every extension offered by every repository the user added. A repository that cannot be
     * reached is skipped rather than failing the whole list, but which ones (and how many of the
     * total) is reported back instead of being silently swallowed.
     */
    suspend fun availableExtensions(): AvailableExtensionsResult = withContext(Dispatchers.IO) {
        access.require()
        val repos = dao.repos()
        val extensions = mutableListOf<Extension.Available>()
        val failed = mutableListOf<String>()
        repos.forEach { repo ->
            try {
                extensions += fetchExtensions(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += repo.name
            }
        }
        AvailableExtensionsResult(extensions = extensions, failedRepoNames = failed, totalRepos = repos.size)
    }

    /**
     * `repo.json` is read on every refresh rather than once when the repository is added, so a
     * repository that moves to the newer index format is picked up without being re-added.
     */
    private suspend fun fetchExtensions(repo: ExtensionRepoEntity): List<Extension.Available> {
        val base = repo.baseUrl
        val indexV2 = fetchRepoMeta(base).indexV2?.takeIf { it.isNotBlank() }
        return if (indexV2 != null) {
            val url = "$base/repo.json".toHttpUrl().resolve(indexV2)
                ?: error("${repo.name} names an index that is not a valid URL: $indexV2")
            val bytes = network.client.newCall(GET(url.toString())).awaitSuccess().body.use { it.bytes() }
            RepoIndexV2.decode(bytes).map { it.toAvailable(repo) }
        } else {
            val response = network.client.newCall(GET("$base/index.min.json")).awaitSuccess()
            val entries = response.body.use { json.decodeFromString<List<IndexEntry>>(it.string()) }
            entries.map { it.toAvailable(repo) }
        }
    }

    private suspend fun fetchRepoMeta(base: String): RepoMeta {
        val response = network.client.newCall(GET("$base/repo.json")).awaitSuccess()
        return response.body.use { json.decodeFromString<RepoMeta>(it.string()) }
    }

    private fun RepoIndexV2.Entry.toAvailable(repo: ExtensionRepoEntity): Extension.Available =
        Extension.Available(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion.toDoubleOrNull() ?: versionName.substringBeforeLast('.').toDoubleOrNull(),
            lang = extensionLang(pkgName, sources.map { it.lang }),
            contentWarning = when (contentRating) {
                2 -> ContentWarning.MIXED
                3 -> ContentWarning.NSFW
                else -> ContentWarning.SAFE
            },
            apkUrl = apkUrl,
            iconUrl = iconUrl,
            repoUrl = repo.baseUrl,
            repoSigningKey = repo.signingKeyFingerprint,
            sources = sources.map {
                Extension.AvailableSource(id = it.id, lang = it.lang, name = it.name, baseUrl = it.baseUrl)
            },
        )

    @Serializable
    private data class RepoMeta(
        val meta: Meta,
        @SerialName("index_v2") val indexV2: String? = null,
    ) {
        @Serializable
        data class Meta(
            val name: String,
            val shortName: String? = null,
            val website: String = "",
            val signingKeyFingerprint: String,
        )
    }

    /** One entry of `index.min.json`. */
    @Serializable
    private data class IndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val code: Long,
        val version: String,
        val nsfw: Int = 0,
        @SerialName("sources") val sources: List<IndexSource>? = null,
    ) {
        fun toAvailable(repo: ExtensionRepoEntity): Extension.Available = Extension.Available(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = pkg,
            versionName = version,
            versionCode = code,
            libVersion = version.substringBeforeLast('.').toDoubleOrNull(),
            lang = lang,
            contentWarning = if (nsfw == 1) ContentWarning.NSFW else ContentWarning.SAFE,
            apkUrl = "${repo.baseUrl}/apk/$apk",
            iconUrl = "${repo.baseUrl}/icon/$pkg.png",
            repoUrl = repo.baseUrl,
            repoSigningKey = repo.signingKeyFingerprint,
            sources = sources.orEmpty().map {
                Extension.AvailableSource(id = it.id, lang = it.lang, name = it.name, baseUrl = it.baseUrl)
            },
        )
    }

    @Serializable
    private data class IndexSource(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String = "",
    )
}

/** Turns whatever the user pasted into the repository's base URL. */
internal fun normalizeRepoBaseUrl(url: String): String {
    val trimmed = url.trim().removeSuffix("/")
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "A repository URL has to start with http:// or https://"
    }
    val withoutFile = REPO_FILES.firstOrNull { trimmed.endsWith("/$it") }
        ?.let { trimmed.removeSuffix("/$it") }
        ?: trimmed
    return withoutFile.removeSuffix("/")
}

private val REPO_FILES = listOf("index.min.json", "index.json", "index.pb", "repo.json")

/**
 * The newer index has no per-extension language, so it is worked out the way [ExtensionLoader]
 * does for an installed extension: its sources' language, or "all" when they differ. A package
 * without sources falls back to the language in its name, `eu.kanade.tachiyomi.extension.<lang>.<name>`.
 */
internal fun extensionLang(pkgName: String, sourceLangs: List<String>): String {
    val distinct = sourceLangs.filter { it.isNotBlank() }.distinct()
    return when (distinct.size) {
        0 -> pkgName.takeIf { it.startsWith(EXTENSION_PACKAGE_PREFIX) }
            ?.split('.')
            ?.takeIf { it.size >= 6 }
            ?.let { it[it.size - 2] }
            .orEmpty()
        1 -> distinct.single()
        else -> "all"
    }
}

private const val EXTENSION_PACKAGE_PREFIX = "eu.kanade.tachiyomi.extension."
