package app.folio.manga.extension

import dalvik.system.PathClassLoader

/**
 * Loads an extension APK's classes, checking itself before the app's own class loader.
 *
 * Delegate-last on purpose: extensions bundle their own Kotlin stdlib and OkHttp, often a
 * different version than Folio's. If the app's classes won the race, an extension built against
 * a newer (or older) API than Folio ships would crash with a LinkageError instead of just using
 * its own copies.
 */
class ExtensionClassLoader(apkPath: String, parent: ClassLoader) :
    PathClassLoader(apkPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            findClass(name)
        } catch (_: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }
}
