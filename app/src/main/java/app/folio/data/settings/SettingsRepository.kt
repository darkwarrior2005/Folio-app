package app.folio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "folio_settings")

/**
 * Settings are stored as one JSON blob: adding a field later just picks up its default, and a
 * backup can carry the whole thing verbatim.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        prefs[KEY]?.let { raw ->
            try {
                json.decodeFromString<AppSettings>(raw)
            } catch (e: Exception) {
                AppSettings()
            }
        } ?: AppSettings()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val currentValue = prefs[KEY]?.let {
                try {
                    json.decodeFromString<AppSettings>(it)
                } catch (e: Exception) {
                    AppSettings()
                }
            } ?: AppSettings()
            prefs[KEY] = json.encodeToString(transform(currentValue))
        }
    }

    suspend fun replaceAll(settings: AppSettings) {
        store.edit { prefs -> prefs[KEY] = json.encodeToString(settings) }
    }

    suspend fun exportJson(): String = json.encodeToString(current())

    suspend fun importJson(raw: String): Boolean = try {
        replaceAll(json.decodeFromString<AppSettings>(raw))
        true
    } catch (e: Exception) {
        false
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    companion object {
        private val KEY = stringPreferencesKey("settings_json")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
