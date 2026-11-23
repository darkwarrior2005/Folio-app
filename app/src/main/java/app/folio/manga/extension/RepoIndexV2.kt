package app.folio.manga.extension

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Reads a Mihon 0.20.1+ repository index (`index.pb`, "index_v2"): gzipped protobuf. Repositories
 * that publish it (Keiyoushi among them) only keep two "update your app" placeholders in their
 * legacy index.min.json, so the real catalogue is only here.
 *
 * Schema, as published by Keiyoushi and its forks (field numbers; unknown fields are skipped):
 * ```
 * Index      { 1 name; 2 shortName; 3 signingKeyFingerprint; 4 Links; 101 ExtensionList }
 * Links      { 1 website; 2 discord }
 * ExtensionList { repeated 1 Extension }
 * Extension  { 1 name; 2 pkg; 3 Files; 4 libVersion ("1.6"); 5 versionCode; 6 versionName;
 *              7 contentRating (1 safe, 2 suggestive, 3 adult); repeated 8 Source }
 * Files      { 1 apkUrl; 2 iconUrl; 501 jarUrl }
 * Source     { 1 id; 2 name; 3 lang; 4 baseUrl }
 * ```
 * A small reader rather than a generated one: the format is tiny and this keeps it testable on
 * the JVM with no codegen.
 */
object RepoIndexV2 {

    data class Index(
        val name: String,
        val shortName: String,
        val signingKeyFingerprint: String,
        val website: String,
        val extensions: List<Extension>,
    )

    data class Extension(
        val name: String,
        val pkg: String,
        val apkUrl: String,
        val iconUrl: String,
        val libVersion: String,
        val versionCode: Long,
        val versionName: String,
        val contentRating: Int,
        val sources: List<Source>,
    ) {
        val isNsfw: Boolean get() = contentRating >= CONTENT_ADULT

        /** "all" or a language code, from the package name (eu.kanade.tachiyomi.extension.<lang>.<name>). */
        val lang: String
            get() = pkg.split('.').getOrNull(4)
                ?: sources.map { it.lang }.distinct().singleOrNull()
                ?: "all"
    }

    data class Source(val id: Long, val name: String, val lang: String, val baseUrl: String)

    const val CONTENT_ADULT = 3

    fun decode(bytes: ByteArray): Index {
        val raw = if (isGzip(bytes)) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        var name = ""
        var shortName = ""
        var fingerprint = ""
        var website = ""
        val extensions = mutableListOf<Extension>()
        ProtoReader(raw).forEachField { field, reader ->
            when (field) {
                1 -> name = reader.string()
                2 -> shortName = reader.string()
                3 -> fingerprint = reader.string()
                4 -> ProtoReader(reader.bytes()).forEachField { f, r -> if (f == 1) website = r.string() else r.skip() }
                101 -> ProtoReader(reader.bytes()).forEachField { f, r ->
                    if (f == 1) extensions += decodeExtension(r.bytes()) else r.skip()
                }
                else -> reader.skip()
            }
        }
        if (extensions.isEmpty() && name.isEmpty()) throw IllegalArgumentException("Not a repository index")
        return Index(name, shortName, fingerprint, website, extensions)
    }

    private fun decodeExtension(bytes: ByteArray): Extension {
        var name = ""
        var pkg = ""
        var apk = ""
        var icon = ""
        var lib = ""
        var code = 0L
        var version = ""
        var rating = 0
        val sources = mutableListOf<Source>()
        ProtoReader(bytes).forEachField { field, r ->
            when (field) {
                1 -> name = r.string()
                2 -> pkg = r.string()
                3 -> ProtoReader(r.bytes()).forEachField { f, fr ->
                    when (f) {
                        1 -> apk = fr.string()
                        2 -> icon = fr.string()
                        else -> fr.skip()
                    }
                }
                4 -> lib = r.string()
                5 -> code = r.varint()
                6 -> version = r.string()
                7 -> rating = r.varint().toInt()
                8 -> sources += decodeSource(r.bytes())
                else -> r.skip()
            }
        }
        return Extension(name, pkg, apk, icon, lib, code, version, rating, sources)
    }

    private fun decodeSource(bytes: ByteArray): Source {
        var id = 0L
        var name = ""
        var lang = ""
        var baseUrl = ""
        ProtoReader(bytes).forEachField { field, r ->
            when (field) {
                1 -> id = r.varint()
                2 -> name = r.string()
                3 -> lang = r.string()
                4 -> baseUrl = r.string()
                else -> r.skip()
            }
        }
        return Source(id, name, lang, baseUrl)
    }

    private fun isGzip(bytes: ByteArray) =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
}

/** Just enough of the protobuf wire format to walk messages field by field. */
internal class ProtoReader(private val buf: ByteArray) {
    private var pos = 0
    private var wireType = 0

    fun forEachField(block: (field: Int, reader: ProtoReader) -> Unit) {
        while (pos < buf.size) {
            val key = varint()
            wireType = (key and 7).toInt()
            val field = (key ushr 3).toInt()
            val before = pos
            block(field, this)
            // A block that didn't consume its value (an unexpected wire type) is skipped here.
            if (pos == before) skip()
        }
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(pos < buf.size) { "Truncated varint" }
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift < 64) { "Varint too long" }
        }
    }

    fun bytes(): ByteArray {
        if (wireType != WIRE_LENGTH) {
            skip()
            return ByteArray(0)
        }
        val length = varint().toInt()
        require(length >= 0 && pos + length <= buf.size) { "Truncated field" }
        return buf.copyOfRange(pos, pos + length).also { pos += length }
    }

    fun string(): String = bytes().decodeToString()

    fun skip() {
        when (wireType) {
            WIRE_VARINT -> varint()
            WIRE_FIXED64 -> pos += 8
            WIRE_LENGTH -> {
                val length = varint().toInt()
                pos += length
            }
            WIRE_FIXED32 -> pos += 4
            else -> throw IllegalArgumentException("Unsupported wire type $wireType")
        }
        require(pos <= buf.size) { "Truncated field" }
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH = 2
        const val WIRE_FIXED32 = 5
    }
}
