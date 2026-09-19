package app.folio.data.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import app.folio.core.model.BookFormat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class FileInfo(
    val uri: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val isDirectory: Boolean = false,
)

/**
 * All file system access goes through here. Files stay where the user keeps them; we hold a
 * persisted URI permission and never rename or delete anything unless explicitly asked.
 */
class FileAccess(val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun uriOf(uriString: String): Uri = Uri.parse(uriString)

    /** Keep read access across reboots. Returns false when the provider refuses. */
    fun takePersistablePermission(uri: Uri, write: Boolean = false): Boolean = try {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
        true
    } catch (e: SecurityException) {
        false
    }

    fun releasePersistablePermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Nothing to release.
        }
    }

    fun hasPersistedPermission(uriString: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == uriString && it.isReadPermission }

    fun exists(uriString: String): Boolean = try {
        val uri = uriOf(uriString)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } == true
            else -> queryInfo(uri) != null
        }
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    fun info(uriString: String): FileInfo? {
        val uri = uriOf(uriString)
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val file = uri.path?.let(::File) ?: return null
                if (!file.exists()) return null
                FileInfo(
                    uri = uriString,
                    name = file.name,
                    size = file.length(),
                    mimeType = mimeFromName(file.name),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory,
                )
            }
            else -> queryInfo(uri)
        }
    }

    private fun queryInfo(uri: Uri): FileInfo? = try {
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(uri, projection, null, null, null)?.use { c: Cursor ->
            if (!c.moveToFirst()) return@use null
            val name = c.getStringOrNull(OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment.orEmpty()
            val mime = c.getStringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE)
            FileInfo(
                uri = uri.toString(),
                name = name,
                size = c.getLongOrNull(OpenableColumns.SIZE) ?: 0L,
                mimeType = mime ?: resolver.getType(uri),
                lastModified = c.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L,
                isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            )
        }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
    }

    fun openInput(uriString: String): InputStream? = try {
        val uri = uriOf(uriString)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { FileInputStream(File(it)) }
        } else {
            resolver.openInputStream(uri)
        }
    } catch (e: Exception) {
        null
    }

    /** Used for writing backups and exports to a location the user picked. */
    fun openOutputStream(uriString: String): java.io.OutputStream? = try {
        val uri = uriOf(uriString)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { java.io.FileOutputStream(File(it)) }
        } else {
            resolver.openOutputStream(uri, "wt")
        }
    } catch (e: Exception) {
        null
    }

    /** Needed by PDFium and by random access into comic archives. */
    fun openFileDescriptor(uriString: String): ParcelFileDescriptor? = try {
        val uri = uriOf(uriString)
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
        } else {
            resolver.openFileDescriptor(uri, "r")
        }
    } catch (e: Exception) {
        null
    }

    /** Copy a file we only have temporary access to (share / "open with") into app storage. */
    fun copyIntoAppStorage(uriString: String, targetDir: File, fileName: String): File? = try {
        targetDir.mkdirs()
        val target = uniqueFile(targetDir, fileName)
        openInput(uriString)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
        } ?: return null
        target
    } catch (e: Exception) {
        null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            candidate = File(dir, "$base-$i$suffix")
            i++
        }
        return candidate
    }

    /** Explicit user action only. Returns false when the provider does not allow deletion. */
    fun deleteFile(uriString: String): Boolean = try {
        val uri = uriOf(uriString)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() } == true
            else -> DocumentsContract.deleteDocument(resolver, uri)
        }
    } catch (e: Exception) {
        false
    }

    /** Explicit user action only ("Rename file"), separate from editing library metadata. */
    fun renameFile(uriString: String, newName: String): String? = try {
        val uri = uriOf(uriString)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val file = uri.path?.let(::File)
                val target = file?.let { File(it.parentFile, newName) }
                if (file != null && target != null && file.renameTo(target)) {
                    Uri.fromFile(target).toString()
                } else {
                    null
                }
            }
            else -> DocumentsContract.renameDocument(resolver, uri, newName)?.toString()
        }
    } catch (e: Exception) {
        null
    }

    fun canDelete(uriString: String): Boolean = try {
        val uri = uriOf(uriString)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).canWrite() } == true
            else -> DocumentFile.fromSingleUri(context, uri)?.canWrite() == true
        }
    } catch (e: Exception) {
        false
    }

    /** Recursively collect importable files from a folder the user picked. */
    fun scanFolder(treeUri: Uri, maxDepth: Int = 6): List<FileInfo> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<FileInfo>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > maxDepth) return
            val children = try {
                dir.listFiles()
            } catch (e: Exception) {
                return
            }
            val images = children.filter { it.isFile && BookFormat.isImageExtension(it.name?.substringAfterLast('.', "")) }
            // A folder made only of images is one comic, not many single pages.
            if (images.size >= MIN_IMAGES_FOR_FOLDER && images.size == children.count { it.isFile }) {
                out += FileInfo(
                    uri = dir.uri.toString(),
                    name = dir.name ?: "",
                    size = images.sumOf { it.length() },
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    lastModified = dir.lastModified(),
                    isDirectory = true,
                )
            } else {
                children.filter { it.isFile }.forEach { file ->
                    val ext = file.name?.substringAfterLast('.', "")?.lowercase()
                    if (ext != null && ext in BookFormat.importableExtensions) {
                        out += FileInfo(
                            uri = file.uri.toString(),
                            name = file.name ?: "",
                            size = file.length(),
                            mimeType = file.type,
                            lastModified = file.lastModified(),
                        )
                    }
                }
            }
            children.filter { it.isDirectory }.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out
    }

    /** Ordered image pages inside a folder-based comic. */
    fun folderImages(treeOrDocUri: String): List<FileInfo> {
        val uri = uriOf(treeOrDocUri)
        val dir = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && BookFormat.isImageExtension(it.name?.substringAfterLast('.', "")) }
            .map {
                FileInfo(
                    uri = it.uri.toString(),
                    name = it.name ?: "",
                    size = it.length(),
                    mimeType = it.type,
                    lastModified = it.lastModified(),
                )
            }
            .sortedWith(compareBy(NaturalOrder) { it.name })
    }

    fun mimeFromName(name: String): String? =
        BookFormat.fromExtension(name.substringAfterLast('.', ""))?.mimeTypes?.firstOrNull()

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val MIN_IMAGES_FOR_FOLDER = 3
    }
}

/** "page2.jpg" sorts before "page10.jpg". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numA = a.substring(i, endA).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, endB).trimStart('0').ifEmpty { "0" }
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
