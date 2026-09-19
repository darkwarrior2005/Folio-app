package app.folio.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.folio.core.model.BookFormat
import app.folio.core.model.TocEntry
import app.folio.core.model.BookLocation
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.files.NaturalOrder
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.BookSource
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/** Ordered pages of a comic, whatever container they came in. */
interface ComicSource {
    val pageCount: Int
    val pageNames: List<String>

    /** Raw image bytes for a page. */
    suspend fun pageBytes(index: Int): ByteArray?

    /** Folders inside the archive become chapters. */
    fun chapters(): List<TocEntry> {
        val names = pageNames
        if (names.isEmpty()) return emptyList()
        val out = mutableListOf<TocEntry>()
        var lastFolder: String? = null
        names.forEachIndexed { index, name ->
            val folder = name.substringBeforeLast('/', "").substringAfterLast('/')
            if (folder.isNotBlank() && folder != lastFolder) {
                lastFolder = folder
                out += TocEntry(
                    title = folder,
                    location = BookLocation(
                        page = index,
                        totalProgression = index.toDouble() / names.size,
                        label = "${index + 1}",
                    ),
                    level = 0,
                )
            }
        }
        return if (out.size > 1) out else emptyList()
    }

    /** Extra metadata shipped inside the archive (ComicInfo.xml), if any. */
    suspend fun comicInfo(): ComicInfo? = null

    fun close()
}

object ComicSources {

    private val IMAGE_EXTENSIONS = BookFormat.IMAGE.extensions

    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS &&
            !name.substringAfterLast('/').startsWith(".") &&
            !name.contains("__MACOSX")

    suspend fun open(
        files: FileAccess,
        cache: CacheStore,
        source: BookSource,
    ): Result<ComicSource> = withContext(Dispatchers.IO) {
        try {
            when (source.format) {
                BookFormat.CBZ -> ZipComicSource.open(files, source.uri)
                BookFormat.CBR -> RarComicSource.open(files, cache, source)
                BookFormat.IMAGE_FOLDER -> FolderComicSource.open(files, source.uri)
                BookFormat.IMAGE -> Result.success(SingleImageSource(files, source.uri, source.fileName))
                else -> Result.failure(ComicOpenException(BookOpenError.Unsupported))
            }
        } catch (e: Exception) {
            Result.failure(ComicOpenException(BookOpenError.Corrupted(e.message)))
        }
    }

    /** Decode a page scaled down to roughly the width it will be drawn at. */
    fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, targetWidthPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        if (sourceWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }
}

class ComicOpenException(val error: BookOpenError) : Exception(error.toString())

/**
 * CBZ read straight from the user's file: commons-compress reads the archive's central directory
 * over a seekable channel, so pages are fetched on demand instead of extracting the whole book.
 */
class ZipComicSource private constructor(
    private val zip: ZipFile,
    private val stream: FileInputStream,
    private val descriptor: android.os.ParcelFileDescriptor,
    private val entries: List<ZipArchiveEntry>,
) : ComicSource {

    private val lock = Mutex()

    override val pageCount: Int = entries.size
    override val pageNames: List<String> = entries.map { it.name }

    override suspend fun pageBytes(index: Int): ByteArray? = lock.withLock {
        val entry = entries.getOrNull(index) ?: return@withLock null
        try {
            zip.getInputStream(entry).use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun comicInfo(): ComicInfo? = lock.withLock {
        val entry = zip.entries.toList().firstOrNull { it.name.substringAfterLast('/').equals(COMIC_INFO, true) }
            ?: return@withLock null
        try {
            ComicInfo.parse(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        runCatching { zip.close() }
        runCatching { stream.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        const val COMIC_INFO = "ComicInfo.xml"

        fun open(files: FileAccess, uri: String): Result<ComicSource> {
            val descriptor = files.openFileDescriptor(uri)
                ?: return Result.failure(ComicOpenException(BookOpenError.FileMissing))
            return try {
                val stream = FileInputStream(descriptor.fileDescriptor)
                val zip = ZipFile.builder().setSeekableByteChannel(stream.channel).get()
                val entries = zip.entries.toList()
                    .filter { !it.isDirectory && ComicSources.isImage(it.name) }
                    .sortedWith(compareBy(NaturalOrder) { it.name })
                if (entries.isEmpty()) {
                    zip.close()
                    stream.close()
                    descriptor.close()
                    Result.failure(ComicOpenException(BookOpenError.Empty))
                } else {
                    Result.success(ZipComicSource(zip, stream, descriptor, entries))
                }
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                // Some CBZ files are stored with formats the random-access reader rejects.
                StreamingZipComicSource.open(files, uri)
            }
        }
    }
}

/** Fallback for archives that cannot be opened for random access: decode by streaming. */
class StreamingZipComicSource private constructor(
    private val files: FileAccess,
    private val uri: String,
    override val pageNames: List<String>,
) : ComicSource {

    private val lock = Mutex()
    override val pageCount: Int = pageNames.size

    override suspend fun pageBytes(index: Int): ByteArray? = lock.withLock {
        val name = pageNames.getOrNull(index) ?: return@withLock null
        try {
            files.openInput(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == name) {
                            val out = ByteArrayOutputStream()
                            zip.copyTo(out)
                            return@use out.toByteArray()
                        }
                        entry = zip.nextEntry
                    }
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() = Unit

    companion object {
        fun open(files: FileAccess, uri: String): Result<ComicSource> = try {
            val names = mutableListOf<String>()
            files.openInput(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && ComicSources.isImage(entry.name)) names += entry.name
                        entry = zip.nextEntry
                    }
                }
            } ?: return Result.failure(ComicOpenException(BookOpenError.FileMissing))
            names.sortWith(NaturalOrder)
            if (names.isEmpty()) {
                Result.failure(ComicOpenException(BookOpenError.Empty))
            } else {
                Result.success(StreamingZipComicSource(files, uri, names))
            }
        } catch (e: Exception) {
            Result.failure(ComicOpenException(BookOpenError.Corrupted(e.message)))
        }
    }
}

/**
 * CBR. junrar needs a real file, so a content URI is staged in the cache on first open.
 * RAR5 archives are not supported by any open source Java decoder and report as unsupported.
 */
class RarComicSource private constructor(
    private val archive: Archive,
    private val headers: List<FileHeader>,
    private val staged: File?,
) : ComicSource {

    private val lock = Mutex()

    override val pageCount: Int = headers.size
    override val pageNames: List<String> = headers.map { it.fileName.replace('\\', '/') }

    override suspend fun pageBytes(index: Int): ByteArray? = lock.withLock {
        val header = headers.getOrNull(index) ?: return@withLock null
        try {
            val out = ByteArrayOutputStream(header.fullUnpackSize.toInt().coerceAtLeast(1024))
            archive.extractFile(header, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        runCatching { archive.close() }
        staged?.delete()
    }

    companion object {
        fun open(files: FileAccess, cache: CacheStore, source: BookSource): Result<ComicSource> {
            val local = localFile(files, cache, source) ?: return Result.failure(
                ComicOpenException(BookOpenError.FileMissing),
            )
            return try {
                val archive = Archive(local.file)
                val headers = archive.fileHeaders
                    .filter { !it.isDirectory && ComicSources.isImage(it.fileName.replace('\\', '/')) }
                    .sortedWith(compareBy(NaturalOrder) { it.fileName.replace('\\', '/') })
                if (headers.isEmpty()) {
                    archive.close()
                    Result.failure(ComicOpenException(BookOpenError.Empty))
                } else {
                    Result.success(RarComicSource(archive, headers, local.staged))
                }
            } catch (e: Exception) {
                local.staged?.delete()
                val unsupported = e.javaClass.simpleName.contains("RarV5", ignoreCase = true) ||
                    e.message?.contains("RAR5", ignoreCase = true) == true
                Result.failure(
                    ComicOpenException(
                        if (unsupported) BookOpenError.Unsupported else BookOpenError.Corrupted(e.message),
                    ),
                )
            }
        }

        private data class LocalFile(val file: File, val staged: File?)

        private fun localFile(files: FileAccess, cache: CacheStore, source: BookSource): LocalFile? {
            val uri = files.uriOf(source.uri)
            if (uri.scheme == "file") {
                val file = uri.path?.let(::File) ?: return null
                return if (file.exists()) LocalFile(file, null) else null
            }
            val target = cache.stagedArchive(source.bookId, "cbr")
            if (target.exists() && target.length() > 0) return LocalFile(target, target)
            val copied = files.copyIntoAppStorage(source.uri, target.parentFile ?: cache.tmpDir, target.name)
                ?: return null
            return LocalFile(copied, copied)
        }
    }
}

/** A folder of images the user picked. */
class FolderComicSource private constructor(
    private val files: FileAccess,
    private val pageUris: List<String>,
    override val pageNames: List<String>,
) : ComicSource {

    override val pageCount: Int = pageUris.size

    override suspend fun pageBytes(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        val uri = pageUris.getOrNull(index) ?: return@withContext null
        try {
            files.openInput(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() = Unit

    companion object {
        fun open(files: FileAccess, treeUri: String): Result<ComicSource> {
            val images = files.folderImages(treeUri)
            return if (images.isEmpty()) {
                Result.failure(ComicOpenException(BookOpenError.Empty))
            } else {
                Result.success(FolderComicSource(files, images.map { it.uri }, images.map { it.name }))
            }
        }
    }
}

class SingleImageSource(
    private val files: FileAccess,
    private val uri: String,
    name: String,
) : ComicSource {
    override val pageCount: Int = 1
    override val pageNames: List<String> = listOf(name)

    override suspend fun pageBytes(index: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (index != 0) return@withContext null
        try {
            files.openInput(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() = Unit
}
