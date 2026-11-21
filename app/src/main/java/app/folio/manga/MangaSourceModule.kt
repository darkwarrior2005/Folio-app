package app.folio.manga

import android.app.Application
import android.content.SharedPreferences
import app.folio.data.settings.SettingsRepository
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * What extensions can reach through Injekt: `Application`, [NetworkHelper], the lenient [Json]
 * Mihon gives extensions (so a field a source forgot to mark optional - Hitomi's
 * `japaneseTitle`, for one - doesn't fail the whole response), [ProtoBuf] (for `index.pb`
 * repositories), [JavaScriptEngine], [AppInfo], and per-source [SharedPreferences].
 */
class MangaSourceModule(private val app: Application, private val settings: SettingsRepository) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<Application> { app }

        addSingletonFactory {
            NetworkHelper(app) {
                val userAgent = runBlocking { settings.current().manga.userAgent }
                userAgent.ifBlank { NetworkHelper.DEFAULT_USER_AGENT }
            }
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
            }
        }

        addSingletonFactory { ProtoBuf { encodeDefaults = true } }

        addSingletonFactory { JavaScriptEngine(app) }

        AppInfo.init(app)
    }

    companion object {
        fun sourcePreferences(app: Application, source: eu.kanade.tachiyomi.source.Source): SharedPreferences =
            app.getSharedPreferences("source_${source.id}", android.content.Context.MODE_PRIVATE)
    }
}
