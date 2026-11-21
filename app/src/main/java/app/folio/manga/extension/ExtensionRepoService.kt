package app.folio.manga.extension

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.injectLazy
import java.util.zip.GZIPInputStream

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

@Serializable
private data class RepoJson(
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
    val code: Int = 0,
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
    private val protoBuf: ProtoBuf by injectLazy()

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

    private suspend fun fetchOne(baseUrl: String): List<AvailableExtension> {
        val fingerprint = fetchSigningKeyFingerprint(baseUrl)

        // index_v2 (Mihon 0.20.1+): gzipped protobuf.
        runCatching {
            val bytes = network.client.newCall(GET("$baseUrl/index.pb")).awaitSuccess().body.bytes()
            val decompressed = GZIPInputStream(bytes.inputStream()).readBytes()
            return RepoIndexV2.decode(protoBuf, decompressed)
                .map { it.toAvailableExtension(baseUrl, fingerprint) }
        }

        // index.min.json fallback.
        val response = network.client.newCall(GET("$baseUrl/index.min.json")).awaitSuccess()
        val list = json.decodeFromString<List<MinJsonExtension>>(response.body.string())
        return list.map {
            AvailableExtension(
                packageName = it.pkg,
                name = it.name,
                versionName = it.version,
                versionCode = it.code.toLong(),
                apkUrl = "$baseUrl/apk/${it.apk}",
                iconUrl = it.icon.ifBlank { null }?.let { icon -> "$baseUrl/icon/$icon" },
                lang = it.lang,
                isNsfw = it.nsfw == 1,
                repoBaseUrl = baseUrl,
                signingKeyFingerprint = fingerprint,
            )
        }
    }

    private suspend fun fetchSigningKeyFingerprint(baseUrl: String): String? = runCatching {
        val response = network.client.newCall(GET("$baseUrl/repo.json")).awaitSuccess()
        json.decodeFromString<RepoJson>(response.body.string()).meta?.signingKeyFingerprint
    }.getOrNull()
}
