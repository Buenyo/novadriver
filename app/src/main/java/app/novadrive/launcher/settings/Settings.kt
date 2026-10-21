package app.novadrive.launcher.settings

import androidx.annotation.DrawableRes
import app.novadrive.launcher.R

enum class ThemeMode(val label: String) {
    AUTO_SUN("Automático"),
    SYSTEM("Sistema"),
    DARK("Escuro"),
    LIGHT("Claro"),
}

enum class FontSize(val label: String, val scale: Float) {
    NORMAL("Normal", 1f),
    LARGE("Grande", 1.15f),
    XLARGE("Muito grande", 1.3f),
}

enum class WallpaperChoice(val label: String) {
    REAR("HR-V traseira"),
    STUDIO("HR-V estúdio"),
    NONE("Sem imagem"),
}

enum class LocationMode(val label: String) {
    GPS("GPS da central"),
    CITY("Cidade fixa"),
}

/**
 * Atalhos da HOME. Os pacotes "conhecidos" são os IDs públicos da Play Store.
 * Rádio e Telefone são apps do fabricante: o nome do pacote varia por firmware, então
 * NÃO é presumido; tentamos achar pelo nome exibido e o usuário pode escolher manualmente.
 */
enum class ShortcutSlot(
    val label: String,
    @DrawableRes val icon: Int,
    val knownPackages: List<String>,
    val labelHint: Regex? = null,
) {
    WAZE("Waze", R.drawable.ic_navigation, listOf("com.waze")),
    MAPS("Google Maps", R.drawable.ic_navigation, listOf("com.google.android.apps.maps")),
    SPOTIFY("Spotify", R.drawable.ic_music, listOf("com.spotify.music")),
    YT_MUSIC("YouTube Music", R.drawable.ic_music, listOf("com.google.android.apps.youtube.music")),
    RADIO("Rádio", R.drawable.ic_radio, emptyList(), Regex("""(?i)\br[aá]dio\b|\bfm\b|\btuner\b""")),
    PHONE("Telefone", R.drawable.ic_phone, emptyList(), Regex("""(?i)bluetooth|telefone|\bphone\b|\bbt\b|discador""")),
}

data class CityLocation(val name: String, val latitude: Double, val longitude: Double)

data class Settings(
    val theme: ThemeMode = ThemeMode.AUTO_SUN,
    val fontSize: FontSize = FontSize.NORMAL,
    val performanceMode: Boolean = false,
    val wallpaper: WallpaperChoice = WallpaperChoice.REAR,
    /** slot -> componente "pacote/classe" escolhido pelo usuário; ausente = automático. */
    val shortcuts: Map<ShortcutSlot, String> = emptyMap(),
    /** Componente escolhido para o botão Android Auto; null = detecção automática. */
    val headunitComponent: String? = null,
    val weatherEnabled: Boolean = true,
    val locationMode: LocationMode = LocationMode.GPS,
    val city: CityLocation? = null,
    val notificationPromptShown: Boolean = false,
    /** Vigia de acessibilidade + abertura no boot: tira o launcher do fabricante da frente. */
    val replaceOemHome: Boolean = true,
)
