package app.folio.manga.extension

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy

data class AvailableExtension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val iconUrl: String?,
    val lang: String,
    val isNsfw: Boolean,
    val repoBaseUrl: String,
    val signingKeyFingerprint: String?,
)

data class AvailableExtensionsResult(
    val extensions: List<AvailableExtension>,
    val failedRepoNames: List<String>,
    val totalRepos: Int,
)

/** What a repository says about itself; stored when it's added. */
data class RepoInfo(val name: String, val website: String?, val signingKeyFingerprint: String?)

@Serializable
private data class RepoJson(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: RepoMeta? = null,
)

@Serializable
private data class RepoMeta(
    val name: String = "",
    val website: String = "",
    val signingKeyFingerprint: String? = null,
)

@Serializable
private data class MinJsonExtension(
    val name: String = "",
    val pkg: String = "",
    val apk: String = "",
    val lang: String = "",
    val code: Long = 0,
    val version: String = "",
    val nsfw: Int = 0,
    val icon: String = "",
)

/**
 * Normalizes a pasted `index.pb`, `index.json` or `repo.json` address to the repository's base
 * URL (the folder those files live in).
 */
fun normalizeRepoUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return trimmed.removeSuffix("/index.pb").removeSuffix("/index.min.json")
        .removeSuffix("/index.json").removeSuffix("/repo.json")
}

class ExtensionRepoService {

    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    suspend fun fetchAll(repoBaseUrls: List<String>): AvailableExtensionsResult {
        val failed = mutableListOf<String>()
        val extensions = mutableListOf<AvailableExtension>()
        for (baseUrl in repoBaseUrls) {
            try {
                extensions += fetchOne(baseUrl)
            } catch (_: Exception) {
                failed += baseUrl
            }
        }
        return AvailableExtensionsResult(extensions, failed, repoBaseUrls.size)
    }

    /** The repository's name and signing key, from repo.json or else its index.pb header. */
    suspend fun fetchInfo(baseUrl: String): RepoInfo? {
        val repoJson = fetchRepoJson(baseUrl)
        val meta = repoJson?.meta
        if (meta != null && meta.name.isNotBlank()) {
            return RepoInfo(meta.name, meta.website.ifBlank { null }, meta.signingKeyFingerprint)
        }
        val index = runCatching { fetchIndexV2(baseUrl, repoJson?.indexV2) }.getOrNull() ?: return null
        return RepoInfo(index.name, index.website.ifBlank { null }, index.signingKeyFingerprint.ifBlank { null })
    }

    private suspend fun fetchOne(baseUrl: String): List<AvailableExtension> {
        val repoJson = fetchRepoJson(baseUrl)

        val index = runCatching { fetchIndexV2(baseUrl, repoJson?.indexV2) }
        index.getOrNull()?.let { v2 ->
            val fingerprint = repoJson?.meta?.signingKeyFingerprint ?: v2.signingKeyFingerprint.ifBlank { null }
            return v2.extensions.map { it.toAvailableExtension(baseUrl, fingerprint) }
        }
        // A repository that publishes index_v2 keeps only "update your app" placeholders in its
        // legacy index, so falling back would show those as if they were real extensions.
        if (repoJson?.indexV2 != null) throw index.exceptionOrNull() ?: IllegalStateException("index_v2 failed")

        val response = network.client.newCall(GET("$baseUrl/index.min.json")).awaitSuccess()
        val list = json.decodeFromString<List<MinJsonExtension>>(response.body.string())
        val fingerprint = repoJson?.meta?.signingKeyFingerprint
        return list.filter { it.pkg.isNotBlank() && it.apk.isNotBlank() }.map {
            AvailableExtension(
                packageName = it.pkg,
                name = it.name.removePrefix("Tachiyomi: "),
                versionName = it.version,
                versionCode = it.code,
                apkUrl = "$baseUrl/apk/${it.apk}",
                iconUrl = "$baseUrl/icon/${it.pkg}.png",
                lang = it.lang,
                isNsfw = it.nsfw == 1,
                repoBaseUrl = baseUrl,
                signingKeyFingerprint = fingerprint,
            )
        }
    }

    /** Tries the address repo.json declares, then index.pb next to it. */
    private suspend fun fetchIndexV2(baseUrl: String, declared: String?): RepoIndexV2.Index {
        val candidates = listOfNotNull(declared, "$baseUrl/index.pb").distinct()
        var lastError: Exception? = null
        for (url in candidates) {
            try {
                val bytes = network.client.newCall(GET(url)).awaitSuccess().use { it.body.bytes() }
                return RepoIndexV2.decode(bytes)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No index")
    }

    private suspend fun fetchRepoJson(baseUrl: String): RepoJson? = runCatching {
        val response = network.client.newCall(GET("$baseUrl/repo.json")).awaitSuccess()
        json.decodeFromString<RepoJson>(response.use { it.body.string() })
    }.getOrNull()
}

private fun RepoIndexV2.Extension.toAvailableExtension(repoBaseUrl: String, fingerprint: String?) =
    AvailableExtension(
        packageName = pkg,
        name = name.removePrefix("Tachiyomi: "),
        versionName = versionName,
        versionCode = versionCode,
        apkUrl = apkUrl,
        iconUrl = iconUrl.ifBlank { null },
        lang = lang,
        isNsfw = isNsfw,
        repoBaseUrl = repoBaseUrl,
        signingKeyFingerprint = fingerprint,
    )
