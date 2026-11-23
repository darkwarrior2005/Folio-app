package app.folio.manga.extension

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * The repository index introduced by Mihon 0.20.1, published as `index.pb` and named by the
 * `index_v2` field of `repo.json`. Repositories that adopted it leave only a placeholder
 * ("Outdated App") in `index.min.json`, so this is the only place their extensions are listed.
 *
 * It is a protobuf message, usually gzip-compressed. Only the fields Folio uses are read;
 * everything else is skipped, so fields added later do not break parsing.
 */
internal object RepoIndexV2 {

    data class Entry(
        val name: String,
        val pkgName: String,
        val apkUrl: String,
        val iconUrl: String,
        val libVersion: String,
        val versionCode: Long,
        val versionName: String,
        /** 1 safe, 2 mixed, 3 adult; 0 when the index does not say. */
        val contentRating: Int,
        val sources: List<Source>,
    )

    data class Source(val id: Long, val name: String, val lang: String, val baseUrl: String)

    fun decode(bytes: ByteArray): List<Entry> {
        val payload = if (bytes.isGzip()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else {
            bytes
        }
        val extensions = mutableListOf<Entry>()
        ProtoReader(payload).forEachField { field, reader ->
            if (field == INDEX_EXTENSIONS) {
                reader.message().forEachField { inner, entry ->
                    if (inner == LIST_ITEM) extensions += entry.message().readExtension()
                    else entry.skip()
                }
            } else {
                reader.skip()
            }
        }
        return extensions
    }

    private fun ProtoReader.readExtension(): Entry {
        var name = ""
        var pkg = ""
        var apk = ""
        var icon = ""
        var lib = ""
        var code = 0L
        var version = ""
        var rating = 0
        val sources = mutableListOf<Source>()
        forEachField { field, reader ->
            when (field) {
                1 -> name = reader.string()
                2 -> pkg = reader.string()
                3 -> reader.message().forEachField { urlField, urls ->
                    when (urlField) {
                        1 -> apk = urls.string()
                        2 -> icon = urls.string()
                        else -> urls.skip()
                    }
                }
                4 -> lib = reader.string()
                5 -> code = reader.varint()
                6 -> version = reader.string()
                7 -> rating = reader.varint().toInt()
                8 -> sources += reader.message().readSource()
                else -> reader.skip()
            }
        }
        return Entry(name, pkg, apk, icon, lib, code, version, rating, sources)
    }

    private fun ProtoReader.readSource(): Source {
        var id = 0L
        var name = ""
        var lang = ""
        var baseUrl = ""
        forEachField { field, reader ->
            when (field) {
                1 -> id = reader.varint()
                2 -> name = reader.string()
                3 -> lang = reader.string()
                4 -> baseUrl = reader.string()
                else -> reader.skip()
            }
        }
        return Source(id, name, lang, baseUrl)
    }

    private fun ByteArray.isGzip() = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()

    private const val INDEX_EXTENSIONS = 101
    private const val LIST_ITEM = 1
}

/** Reads protobuf wire format: a sequence of (field number, wire type, value). */
internal class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val end: Int = buf.size) {

    private var wireType = -1

    fun forEachField(action: (field: Int, reader: ProtoReader) -> Unit) {
        while (pos < end) {
            val tag = varint()
            wireType = (tag and 7).toInt()
            val field = (tag ushr 3).toInt()
            val before = pos
            action(field, this)
            check(pos > before) { "Field $field was not consumed" }
        }
        check(pos == end) { "Malformed protobuf: read past the end of a message" }
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            check(pos < end) { "Malformed protobuf: truncated varint" }
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        error("Malformed protobuf: varint too long")
    }

    fun string(): String {
        val length = length()
        return String(buf, pos, length, Charsets.UTF_8).also { pos += length }
    }

    fun message(): ProtoReader {
        val length = length()
        return ProtoReader(buf, pos, pos + length).also { pos += length }
    }

    fun skip() {
        when (wireType) {
            WIRE_VARINT -> varint()
            WIRE_FIXED64 -> advance(8)
            WIRE_LEN -> advance(length())
            WIRE_FIXED32 -> advance(4)
            else -> error("Unsupported protobuf wire type $wireType")
        }
    }

    private fun length(): Int {
        check(wireType == WIRE_LEN) { "Expected a length-delimited field, got wire type $wireType" }
        val length = varint()
        check(length in 0..(end - pos).toLong()) { "Malformed protobuf: length $length overruns the message" }
        return length.toInt()
    }

    private fun advance(count: Int) {
        check(count <= end - pos) { "Malformed protobuf: truncated field" }
        pos += count
    }

    private companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LEN = 2
        const val WIRE_FIXED32 = 5
    }
}
