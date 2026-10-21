package app.novadrive.launcher.weather

import android.content.Context
import android.util.Log
import app.novadrive.launcher.settings.LocationMode
import app.novadrive.launcher.settings.Settings
import app.novadrive.launcher.settings.SettingsRepository
import app.novadrive.launcher.system.Diagnostics
import app.novadrive.launcher.system.networkAvailableFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.Calendar
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

sealed interface WeatherState {
    data object Disabled : WeatherState
    data object NeedsLocation : WeatherState
    data object Loading : WeatherState
    data class Ready(val weather: Weather, val placeName: String?, val stale: Boolean) : WeatherState
    data class Error(val message: String, val last: Weather?) : WeatherState
}

/** Clima com cache de 30 min. Atualiza só com o HOME visível e quando a rede volta. */
class WeatherRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val location: LocationSource,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var jobs: List<Job> = emptyList()
    private var failures = 0

    init {
        readCache()?.let { (w, name) -> _state.value = WeatherState.Ready(w, name, stale = isStale(w)) }
    }

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch {
                settings.settings.filterNotNull().first()
                while (isActive) {
                    refresh(force = false)
                    delay(nextDelayMs())
                }
            },
            scope.launch {
                // Rede voltou (hotspot do celular conectou): tenta na hora se o dado está velho.
                networkAvailableFlow(context).collect { online -> if (online) refresh(force = false) }
            },
            scope.launch {
                // Mudou cidade/modo/ligou o clima: busca de novo.
                settings.settings.filterNotNull()
                    .map { Triple(it.weatherEnabled, it.locationMode, it.city) }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { refresh(force = true) }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    fun refreshNow() {
        scope.launch { refresh(force = true) }
    }

    private suspend fun refresh(force: Boolean) = mutex.withLock {
        val s = settings.settings.value ?: return@withLock
        if (!s.weatherEnabled) {
            _state.value = WeatherState.Disabled
            return@withLock
        }
        val cached = readCache()
        if (!force && cached != null && !isStale(cached.first)) {
            _state.value = WeatherState.Ready(cached.first, cached.second, stale = false)
            return@withLock
        }
        val target = resolveLocation(s) ?: run {
            _state.value = cached?.let { WeatherState.Ready(it.first, it.second, stale = true) } ?: WeatherState.NeedsLocation
            return@withLock
        }
        if (clockLooksWrong()) {
            _state.value = WeatherState.Error("Data/hora da central incorreta", cached?.first)
            return@withLock
        }
        if (cached == null) _state.value = WeatherState.Loading
        try {
            val w = withContext(Dispatchers.IO) { OpenMeteo.forecast(target.first, target.second) }
            failures = 0
            writeCache(w, target.third)
            _state.value = WeatherState.Ready(w, target.third, stale = false)
        } catch (e: Exception) {
            failures++
            Log.w(Diagnostics.TAG, "Falha ao buscar clima (${e.javaClass.simpleName})")
            _state.value = WeatherState.Error(messageFor(e), cached?.first)
        }
    }

    private suspend fun resolveLocation(s: Settings): Triple<Double, Double, String?>? = when (s.locationMode) {
        LocationMode.CITY -> s.city?.let { Triple(it.latitude, it.longitude, it.name.substringBefore(',')) }
        LocationMode.GPS -> location.current()?.let { Triple(round2(it.latitude), round2(it.longitude), null) }
    }

    /** Arredonda para ~1 km: o suficiente para o clima, sem enviar a posição exata do carro. */
    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0

    private fun nextDelayMs(): Long {
        if (failures == 0) return REFRESH_MS
        // 1, 2, 4, 8... minutos até 30.
        return (60_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(REFRESH_MS)
    }

    private fun isStale(w: Weather) = System.currentTimeMillis() - w.fetchedAt !in 0..REFRESH_MS

    /** Central sem bateria de RTC volta para 1970/2000 ao ligar; HTTPS falha até acertar. */
    private fun clockLooksWrong() = Calendar.getInstance().get(Calendar.YEAR) < 2025

    private fun messageFor(e: Exception): String = when (e) {
        is UnknownHostException -> "Sem internet"
        is SSLException -> "Erro de segurança (confira data/hora)"
        is IOException -> "Sem conexão com o servidor"
        else -> "Clima indisponível"
    }

    private fun readCache(): Pair<Weather, String?>? = runCatching {
        val raw = prefs.getString("json", null) ?: return null
        val o = JSONObject(raw)
        Weather(
            temperatureC = o.getDouble("t"),
            code = o.getInt("c"),
            isDay = o.getBoolean("d"),
            sunriseMin = o.optInt("sr", -1).takeIf { it >= 0 },
            sunsetMin = o.optInt("ss", -1).takeIf { it >= 0 },
            fetchedAt = o.getLong("at"),
        ) to o.optString("n").takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeCache(w: Weather, name: String?) {
        val o = JSONObject()
            .put("t", w.temperatureC).put("c", w.code).put("d", w.isDay)
            .put("sr", w.sunriseMin ?: -1).put("ss", w.sunsetMin ?: -1)
            .put("at", w.fetchedAt).put("n", name ?: "")
        prefs.edit().putString("json", o.toString()).apply()
    }

    private companion object {
        const val REFRESH_MS = 30 * 60_000L
    }
}
