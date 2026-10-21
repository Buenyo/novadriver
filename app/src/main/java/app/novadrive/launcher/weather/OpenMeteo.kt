package app.novadrive.launcher.weather

import app.novadrive.launcher.settings.CityLocation
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class Weather(
    val temperatureC: Double,
    val code: Int,
    val isDay: Boolean,
    /** Minutos desde 00:00 no fuso local da coordenada. */
    val sunriseMin: Int?,
    val sunsetMin: Int?,
    val fetchedAt: Long,
)

/**
 * Cliente mínimo do Open-Meteo (sem chave, uso não comercial). HttpURLConnection + org.json
 * já existem no Android: zero dependências. Formato conferido em 2026-10-20:
 * forecast → current.{temperature_2m, weather_code, is_day}, daily.{sunrise[], sunset[]} "AAAA-MM-DDTHH:MM".
 */
object OpenMeteo {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    private const val MAX_BYTES = 64 * 1024

    fun forecastUrl(lat: Double, lon: Double): String =
        // Locale.US: em pt-BR o separador decimal seria vírgula e a URL quebraria.
        String.format(
            Locale.US,
            "%s?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day" +
                "&daily=sunrise,sunset&timezone=auto&forecast_days=1",
            FORECAST, lat, lon,
        )

    fun forecast(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): Weather =
        parseForecast(get(forecastUrl(lat, lon)), now)

    fun parseForecast(json: String, now: Long): Weather {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        return Weather(
            temperatureC = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            sunriseMin = daily?.optJSONArray("sunrise")?.optString(0)?.let(::minutesOf),
            sunsetMin = daily?.optJSONArray("sunset")?.optString(0)?.let(::minutesOf),
            fetchedAt = now,
        )
    }

    fun searchCity(query: String): List<CityLocation> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return parseCities(get("$GEOCODING?name=$q&count=8&language=pt&format=json"))
    }

    fun parseCities(json: String): List<CityLocation> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val o = results.getJSONObject(i)
            val name = listOf(o.optString("name"), o.optString("admin1"), o.optString("country_code"))
                .filter { it.isNotBlank() }
                .joinToString(", ")
            CityLocation(name, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** "2026-10-20T05:29" → 329 */
    fun minutesOf(iso: String): Int? {
        val t = iso.substringAfter('T', "")
        val h = t.substringBefore(':').toIntOrNull() ?: return null
        val m = t.substringAfter(':', "").take(2).toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "NovaDrive/1.0 (Android launcher)")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_BYTES) throw IOException("Resposta grande demais")
                }
                return buf.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }
}

/** Códigos WMO usados pelo Open-Meteo → texto pt-BR e símbolo (emoji Unicode 6, existe no Android 7). */
object WeatherCode {
    fun description(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Poucas nuvens"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Garoa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80, 81 -> "Pancadas de chuva"
        82 -> "Pancadas fortes"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoadas"
        96, 99 -> "Trovoadas com granizo"
        else -> "—"
    }

    fun symbol(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀" else "☾"
        1, 2 -> if (isDay) "⛅" else "☁"
        3 -> "☁"
        45, 48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "·"
    }
}
