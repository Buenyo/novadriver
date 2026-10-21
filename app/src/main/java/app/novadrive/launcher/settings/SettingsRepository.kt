package app.novadrive.launcher.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    /** null até a primeira leitura do disco terminar (dezenas de ms em flash lenta). */
    val settings: StateFlow<Settings?> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::toSettings)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val current: Settings get() = settings.value ?: Settings()

    fun setTheme(v: ThemeMode) = edit { it[THEME] = v.name }
    fun setFontSize(v: FontSize) = edit { it[FONT] = v.name }
    fun setPerformanceMode(v: Boolean) = edit { it[PERF] = v }
    fun setWallpaper(v: WallpaperChoice) = edit { it[WALLPAPER] = v.name }
    fun setWeatherEnabled(v: Boolean) = edit { it[WEATHER] = v }
    fun setLocationMode(v: LocationMode) = edit { it[LOC_MODE] = v.name }
    fun setNotificationPromptShown() = edit { it[NOTIF_PROMPT] = true }

    fun setShortcut(slot: ShortcutSlot, component: String?) = edit {
        val key = shortcutKey(slot)
        if (component == null) it.remove(key) else it[key] = component
    }

    fun setHeadunit(component: String?) = edit {
        if (component == null) it.remove(HEADUNIT) else it[HEADUNIT] = component
    }

    fun setCity(city: CityLocation) = edit {
        it[CITY_NAME] = city.name
        it[CITY_LAT] = city.latitude
        it[CITY_LON] = city.longitude
        it[LOC_MODE] = LocationMode.CITY.name
    }

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { store.edit(block) }
    }

    private fun toSettings(p: Preferences): Settings {
        val d = Settings()
        val lat = p[CITY_LAT]
        val lon = p[CITY_LON]
        val name = p[CITY_NAME]
        return Settings(
            theme = enumOr(p[THEME], d.theme),
            fontSize = enumOr(p[FONT], d.fontSize),
            performanceMode = p[PERF] ?: d.performanceMode,
            wallpaper = enumOr(p[WALLPAPER], d.wallpaper),
            shortcuts = ShortcutSlot.entries.mapNotNull { s -> p[shortcutKey(s)]?.let { s to it } }.toMap(),
            headunitComponent = p[HEADUNIT],
            weatherEnabled = p[WEATHER] ?: d.weatherEnabled,
            locationMode = enumOr(p[LOC_MODE], d.locationMode),
            city = if (lat != null && lon != null && name != null) CityLocation(name, lat, lon) else null,
            notificationPromptShown = p[NOTIF_PROMPT] ?: false,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(raw: String?, default: E): E =
        raw?.let { r -> enumValues<E>().firstOrNull { it.name == r } } ?: default

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FONT = stringPreferencesKey("font_size")
        val PERF = booleanPreferencesKey("performance_mode")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        val HEADUNIT = stringPreferencesKey("headunit_component")
        val WEATHER = booleanPreferencesKey("weather_enabled")
        val LOC_MODE = stringPreferencesKey("location_mode")
        val CITY_NAME = stringPreferencesKey("city_name")
        val CITY_LAT = doublePreferencesKey("city_lat")
        val CITY_LON = doublePreferencesKey("city_lon")
        val NOTIF_PROMPT = booleanPreferencesKey("notification_prompt_shown")

        fun shortcutKey(slot: ShortcutSlot) = stringPreferencesKey("shortcut_${slot.name.lowercase()}")
    }
}
