package app.novadrive.launcher.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.novadrive.launcher.settings.ThemeMode

val HondaRed = Color(0xFFE5132D)
private val HondaRedDeep = Color(0xFFB00E22)

val DarkColors: ColorScheme = darkColorScheme(
    primary = HondaRed,
    onPrimary = Color.White,
    primaryContainer = HondaRedDeep,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE8D6D8),
    onSecondary = Color(0xFF2A1316),
    background = Color(0xFF0B0506),
    onBackground = Color(0xFFF7F1F1),
    surface = Color(0xFF140A0C),
    onSurface = Color(0xFFF7F1F1),
    surfaceVariant = Color(0xFF2A1619),
    onSurfaceVariant = Color(0xFFCDBCBE),
    outline = Color(0xFF5E3D42),
    outlineVariant = Color(0xFF3A2427),
)

private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFFC8102E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB),
    onPrimaryContainer = Color(0xFF40000A),
    secondary = Color(0xFF5E3D42),
    onSecondary = Color.White,
    background = Color(0xFFF7F2F2),
    onBackground = Color(0xFF1C1214),
    surface = Color(0xFFFFFBFB),
    onSurface = Color(0xFF1C1214),
    surfaceVariant = Color(0xFFF1DEDF),
    onSurfaceVariant = Color(0xFF524345),
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD8C2C3),
)

/** Tipografia leve (Roboto Light do sistema, sem fonte extra no APK) e grande para leitura no carro. */
private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 76.sp, letterSpacing = (-1).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, fontSize = 60.sp, letterSpacing = (-0.5).sp),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 40.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.4.sp),
    )
}

/** Modo performance (ou animações do sistema desligadas): nada de animação. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** true quando a HOME está sobre o papel de parede (texto claro, cartões translúcidos). */
val LocalOnWallpaper = staticCompositionLocalOf { true }

fun <T> motion(reduce: Boolean, durationMs: Int = 180): FiniteAnimationSpec<T> =
    if (reduce) snap() else tween(durationMs)

object ThemeResolver {
    const val DEFAULT_SUNRISE = 6 * 60
    const val DEFAULT_SUNSET = 18 * 60

    /** Modo automático: escuro entre o pôr e o nascer do sol (do clima) ou 18h–6h sem dados. */
    fun isDark(mode: ThemeMode, systemDark: Boolean, minuteOfDay: Int, sunriseMin: Int?, sunsetMin: Int?): Boolean =
        when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.AUTO_SUN -> {
                val rise = sunriseMin ?: DEFAULT_SUNRISE
                val set = sunsetMin ?: DEFAULT_SUNSET
                if (rise < set) minuteOfDay < rise || minuteOfDay >= set else minuteOfDay in set until rise
            }
        }
}

@Composable
fun NovaDriveTheme(dark: Boolean, fontScale: Float, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, d.fontScale * fontScale),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else Light, typography = typography(), content = content)
    }
}

/** Sombra de texto barata (sem blur pesado) para legibilidade sobre a foto. */
fun TextStyle.onPhoto(enabled: Boolean): TextStyle =
    if (!enabled) this else copy(shadow = androidx.compose.ui.graphics.Shadow(Color(0xB3000000), blurRadius = 6f))
