package app.folio.manga.extension

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Decodes a Keiyoushi-style `index.pb` (gzipped protobuf `index_v2`), Mihon 0.20.1+'s repository
 * index format. Field numbers follow the publicly documented ext-indexer schema; `index.min.json`
 * (handled in [ExtensionRepoService]) stays the unambiguous fallback for anything older or if a
 * repository's `.pb` ever drifts from this.
 */
object RepoIndexV2 {

    @Serializable
    data class IndexSource(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val id: Long = 0,
        @ProtoNumber(3) val baseUrl: String = "",
        @ProtoNumber(5) val lang: String = "",
    )

    @Serializable
    data class IndexExtension(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val pkg: String = "",
        @ProtoNumber(3) val apk: String = "",
        @ProtoNumber(4) val lang: String = "",
        @ProtoNumber(5) val code: Int = 0,
        @ProtoNumber(6) val version: String = "",
        @ProtoNumber(7) val nsfw: Boolean = false,
        @ProtoNumber(8) val sources: List<IndexSource> = emptyList(),
        @ProtoNumber(9) val icon: String = "",
    )

    /** `index.pb` is a bare, ungzipped-by-the-time-it-gets-here repeated-message stream. */
    fun decode(proto: ProtoBuf, bytes: ByteArray): List<IndexExtension> =
        proto.decodeFromByteArray(ListSerializerWorkaround, bytes)

    // kotlinx-serialization-protobuf needs an explicit serializer for a top-level repeated field.
    private val ListSerializerWorkaround = kotlinx.serialization.builtins.ListSerializer(IndexExtension.serializer())
}

fun RepoIndexV2.IndexExtension.toAvailableExtension(repoBaseUrl: String, signingKeyFingerprint: String?) =
    AvailableExtension(
        packageName = pkg,
        name = name,
        versionName = version,
        versionCode = code.toLong(),
        apkUrl = "$repoBaseUrl/apk/$apk",
        iconUrl = icon.ifBlank { null }?.let { "$repoBaseUrl/icon/$it" },
        lang = lang,
        isNsfw = nsfw,
        repoBaseUrl = repoBaseUrl,
        signingKeyFingerprint = signingKeyFingerprint,
    )
