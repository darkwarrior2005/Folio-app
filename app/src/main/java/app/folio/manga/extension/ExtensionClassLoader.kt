package app.folio.manga.extension

import android.os.Build
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * Loads an extension apk's classes with its own copies in front of Folio's.
 *
 * An extension is compiled against its own copies of Kotlin, OkHttp and the source API. Normal
 * parent-first lookup would hand it Folio's versions and break it, so lookup order is: the boot
 * classes, then the apk, then Folio. [DelegateLastClassLoader] does exactly that but only exists
 * from API 27, so API 26 gets the same order built on [PathClassLoader].
 *
 * Adapted from Mihon (Apache-2.0).
 */
internal fun extensionClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
): ClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
    DelegateLastClassLoader(dexPath, librarySearchPath, parent)
} else {
    DelegateLastPathClassLoader(dexPath, librarySearchPath, parent)
}

private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // Not a platform class; try the apk next.
            }
        }

        val fromApk = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }

        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            // Report the apk's failure, which is the useful one.
            throw fromApk
        }
    }

    override fun getResource(name: String?): URL? =
        bootClassLoader?.getResource(name) ?: findResource(name) ?: parent?.getResource(name)

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
