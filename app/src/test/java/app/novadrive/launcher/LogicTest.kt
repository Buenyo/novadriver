package app.novadrive.launcher

import app.novadrive.launcher.settings.ThemeMode
import app.novadrive.launcher.system.CrashGuard
import app.novadrive.launcher.ui.theme.ThemeResolver
import app.novadrive.launcher.weather.OpenMeteo
import app.novadrive.launcher.weather.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolverTest {
    private fun dark(min: Int, rise: Int? = 329, set: Int? = 1093) =
        ThemeResolver.isDark(ThemeMode.AUTO_SUN, systemDark = false, minuteOfDay = min, sunriseMin = rise, sunsetMin = set)

    @Test fun autoUsesSunTimes() {
        assertTrue(dark(5 * 60))          // 05:00, antes do nascer (05:29)
        assertFalse(dark(12 * 60))
        assertTrue(dark(18 * 60 + 13))    // exatamente no pôr do sol
        assertTrue(dark(23 * 60))
    }

    @Test fun autoFallsBackTo6And18() {
        assertTrue(dark(5 * 60 + 59, null, null))
        assertFalse(dark(6 * 60, null, null))
        assertTrue(dark(18 * 60, null, null))
    }

    @Test fun fixedModes() {
        assertTrue(ThemeResolver.isDark(ThemeMode.DARK, false, 720, null, null))
        assertFalse(ThemeResolver.isDark(ThemeMode.LIGHT, true, 0, null, null))
        assertTrue(ThemeResolver.isDark(ThemeMode.SYSTEM, true, 720, null, null))
    }
}

class CrashGuardTest {
    @Test fun threeCrashesInWindowEntersSafeMode() {
        val now = 1_000_000L
        assertTrue(CrashGuard.shouldEnterSafeMode(listOf(now - 90_000, now - 30_000, now - 1_000), now))
    }

    @Test fun oldCrashesAreIgnored() {
        val now = 1_000_000L
        assertFalse(CrashGuard.shouldEnterSafeMode(listOf(now - 500_000, now - 30_000, now - 1_000), now))
    }

    @Test fun parseToleratesGarbage() {
        assertEquals(listOf(1L, 3L), CrashGuard.parse("1,x,3"))
        assertEquals(emptyList<Long>(), CrashGuard.parse(null))
    }
}

class OpenMeteoTest {
    // Resposta real capturada em 2026-10-20 (São Paulo).
    private val sample = """{"latitude":-23.514938,"longitude":-46.610504,"timezone":"America/Sao_Paulo",
        "current":{"time":"2026-10-20T22:45","interval":900,"temperature_2m":13.9,"weather_code":3,"is_day":0},
        "daily":{"time":["2026-10-20"],"sunrise":["2026-10-20T05:29"],"sunset":["2026-10-20T18:13"]}}"""

    @Test fun parsesForecast() {
        val w = OpenMeteo.parseForecast(sample, now = 42)
        assertEquals(13.9, w.temperatureC, 0.001)
        assertEquals(3, w.code)
        assertFalse(w.isDay)
        assertEquals(5 * 60 + 29, w.sunriseMin)
        assertEquals(18 * 60 + 13, w.sunsetMin)
        assertEquals(42L, w.fetchedAt)
    }

    @Test fun urlUsesDotDecimalEvenInPtBr() {
        val prev = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
        try {
            val url = OpenMeteo.forecastUrl(-23.5149, -46.6105)
            assertTrue(url, url.contains("latitude=-23.51&longitude=-46.61"))
        } finally {
            java.util.Locale.setDefault(prev)
        }
    }

    @Test fun parsesCities() {
        val json = """{"results":[{"name":"Campinas","latitude":-22.90556,"longitude":-47.06083,"country_code":"BR","admin1":"São Paulo"}]}"""
        val c = OpenMeteo.parseCities(json).single()
        assertEquals("Campinas, São Paulo, BR", c.name)
        assertEquals(-22.90556, c.latitude, 1e-6)
        assertTrue(OpenMeteo.parseCities("""{"generationtime_ms":0.1}""").isEmpty())
    }

    @Test fun minutesOfHandlesBadInput() {
        assertEquals(0, OpenMeteo.minutesOf("2026-10-20T00:00"))
        assertNull(OpenMeteo.minutesOf("garbage"))
    }

    @Test fun weatherCodes() {
        assertEquals("Nublado", WeatherCode.description(3))
        assertEquals("Trovoadas", WeatherCode.description(95))
        assertEquals("—", WeatherCode.description(1234))
    }
}
