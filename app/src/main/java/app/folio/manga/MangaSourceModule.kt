package app.folio.manga

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory

/**
 * What an extension is allowed to ask Folio for.
 *
 * Extensions do not receive their dependencies; they look them up through Injekt, which is how
 * the Tachiyomi and Mihon ecosystem has always worked. Only what is registered here is
 * reachable, so this list is the extension API's whole surface beyond the source classes.
 *
 * Mihon also exposes an XML parser. An extension that asks for it will fail to load here until it
 * is added.
 */
class MangaSourceModule(
    private val context: Context,
    private val network: NetworkHelper,
) : InjektModule {

    @OptIn(ExperimentalSerializationApi::class)
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<Context> { context }
        (context as? Application)?.let { addSingleton(it) }
        addSingletonFactory { network }
        addSingletonFactory { extensionJson }
        addSingletonFactory<ProtoBuf> { ProtoBuf }
        addSingletonFactory { JavaScriptEngine(context) }
    }

    companion object {
        /**
         * Lenient on purpose: a source's own responses are not Folio's schema to control, and a
         * field a site added should not stop a chapter list from parsing.
         *
         * `explicitNulls = false` is what Mihon gives extensions too: a nullable field the site
         * left out reads as null. Extensions are written against that, so without it a source
         * such as Hitomi fails with "Field '...' is required ... but it was missing".
         */
        @OptIn(ExperimentalSerializationApi::class)
        private val extensionJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        /**
         * Must run before any extension class is created, since that is when a source's
         * `injectLazy` lookups resolve.
         */
        fun install(context: Context, network: NetworkHelper) {
            Injekt.importModule(MangaSourceModule(context, network))
        }
    }
}
