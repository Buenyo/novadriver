package app.novadrive.launcher.ui.home

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.novadrive.launcher.R
import app.novadrive.launcher.apps.ResolvedShortcut
import app.novadrive.launcher.media.MediaState
import app.novadrive.launcher.media.NowPlaying
import app.novadrive.launcher.settings.Settings
import app.novadrive.launcher.settings.ShortcutSlot
import app.novadrive.launcher.settings.WallpaperChoice
import app.novadrive.launcher.ui.AppIcon
import app.novadrive.launcher.ui.GlassCard
import app.novadrive.launcher.ui.LauncherActions
import app.novadrive.launcher.ui.LauncherViewModel
import app.novadrive.launcher.ui.PickTarget
import app.novadrive.launcher.ui.PillButton
import app.novadrive.launcher.ui.RoundIconButton
import app.novadrive.launcher.ui.Screen
import app.novadrive.launcher.ui.WallpaperLoader
import app.novadrive.launcher.ui.tap
import app.novadrive.launcher.ui.theme.LocalReduceMotion
import app.novadrive.launcher.ui.theme.motion
import app.novadrive.launcher.ui.theme.onPhoto
import app.novadrive.launcher.weather.WeatherCode
import app.novadrive.launcher.weather.WeatherState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val PtBr = Locale.forLanguageTag("pt-BR")

/** Altura útil pequena (barras do sistema grandes, telas 800x480): layout mais enxuto. */
private val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun HomeScreen(vm: LauncherViewModel, actions: LauncherActions, settings: Settings) {
    val onPhoto = settings.wallpaper != WallpaperChoice.NONE
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        Wallpaper(vm.c.wallpaper, settings.wallpaper)
        CompositionLocalProvider(LocalCompact provides compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 14.dp)) {
            TopBar(vm, actions, onPhoto, vm.c.wallpaper.brandingPresent)
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                NowPlayingCard(vm, actions, Modifier.weight(0.35f).fillMaxHeight())
                // Centro livre: é onde o HR-V aparece na foto.
                Spacer(Modifier.weight(0.30f))
                ShortcutGrid(vm, actions, Modifier.weight(0.35f).fillMaxHeight())
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Dock(vm, actions)
        }
        }
    }
}

@Composable
private fun Wallpaper(loader: WallpaperLoader, choice: WallpaperChoice) {
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val image: ImageBitmap? by produceState<ImageBitmap?>(null, choice, w, h) { value = loader.load(choice, w, h) }
        val alpha by animateFloatAsState(if (image != null) 1f else 0f, motion(reduce, 260), label = "wall")
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF4A0A12), Color(0xFF0B0506)),
                    center = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.62f),
                    radius = maxOf(w, h) * 0.7f,
                ),
            ),
        )
        image?.let {
            Image(it, null, Modifier.fillMaxSize().alpha(alpha), contentScale = ContentScale.FillBounds)
        }
        // Escurece topo e base para o texto claro ficar legível; o centro (carro) fica intacto.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.62f),
                    0.30f to Color.Transparent,
                    0.78f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.70f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.34f to Color.Transparent,
                    0.66f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
        )
    }
}

@Composable
private fun TopBar(vm: LauncherViewModel, actions: LauncherActions, onPhoto: Boolean, brand: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ClockBlock(vm, onPhoto, Modifier.weight(1f))
        if (brand) {
            Image(
                painterResource(R.drawable.logo_brand),
                contentDescription = stringResource(R.string.brand_logo_description),
                modifier = Modifier.height(34.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                "NOVADRIVE",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp, fontWeight = FontWeight.Light).onPhoto(onPhoto),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        WeatherBlock(vm, actions, onPhoto, Modifier.weight(1f))
    }
}

@Composable
private fun ClockBlock(vm: LauncherViewModel, onPhoto: Boolean, modifier: Modifier) {
    val now by vm.clock.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", PtBr) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d 'de' MMMM", PtBr) }
    val date = Date(now)
    Column(modifier) {
        Text(
            timeFmt.format(date),
            style = (if (LocalCompact.current) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium).onPhoto(onPhoto),
            color = Color.White,
            maxLines = 1,
        )
        Text(
            dateFmt.format(date).replaceFirstChar { it.titlecase(PtBr) },
            style = MaterialTheme.typography.bodyLarge.onPhoto(onPhoto),
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherBlock(vm: LauncherViewModel, actions: LauncherActions, onPhoto: Boolean, modifier: Modifier) {
    val state by vm.weather.collectAsStateWithLifecycle()
    val textColor = Color.White
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        when (val s = state) {
            WeatherState.Disabled -> Unit
            WeatherState.Loading -> Text("Clima…", color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge.onPhoto(onPhoto))
            WeatherState.NeedsLocation -> Text(
                "Configurar clima",
                color = textColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge.onPhoto(onPhoto),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).tap { vm.open(Screen.Settings) }.padding(16.dp),
            )
            is WeatherState.Ready -> WeatherReading(
                temp = s.weather.temperatureC, code = s.weather.code, isDay = s.weather.isDay,
                caption = listOfNotNull(WeatherCode.description(s.weather.code), s.placeName).joinToString(" · "),
                dim = s.stale, onPhoto = onPhoto, onClick = vm.c.weather::refreshNow,
            )
            is WeatherState.Error -> {
                val last = s.last
                if (last != null) {
                    WeatherReading(last.temperatureC, last.code, last.isDay, s.message, dim = true, onPhoto = onPhoto, onClick = vm.c.weather::refreshNow)
                } else {
                    Text(s.message, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge.onPhoto(onPhoto))
                }
            }
        }
    }
}

@Composable
private fun WeatherReading(temp: Double, code: Int, isDay: Boolean, caption: String, dim: Boolean, onPhoto: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.alpha(if (dim) 0.65f else 1f).clip(RoundedCornerShape(16.dp)).tap(onClick = onClick).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(WeatherCode.symbol(code, isDay), fontSize = 34.sp, color = Color.White, style = MaterialTheme.typography.displaySmall.onPhoto(onPhoto))
            Spacer(Modifier.width(10.dp))
            Text("${temp.roundToInt()}°", style = MaterialTheme.typography.displaySmall.onPhoto(onPhoto), color = Color.White)
        }
        Text(caption, style = MaterialTheme.typography.bodyMedium.onPhoto(onPhoto), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
    }
}

// ---------------- Tocando agora ----------------

@Composable
private fun NowPlayingCard(vm: LauncherViewModel, actions: LauncherActions, modifier: Modifier) {
    val state by vm.media.collectAsStateWithLifecycle()
    GlassCard(modifier) {
        when (val s = state) {
            MediaState.NoPermission -> MediaMessage(
                title = "Tocando agora",
                body = "Permita o acesso às notificações para ver e controlar a música de qualquer app.",
                button = "Permitir acesso",
                onClick = { vm.open(Screen.NotificationAccess) },
            )
            MediaState.Idle -> {
                val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
                val music = shortcuts.firstOrNull { it.slot == ShortcutSlot.SPOTIFY && it.app != null }
                    ?: shortcuts.firstOrNull { it.slot == ShortcutSlot.YT_MUSIC && it.app != null }
                MediaMessage(
                    title = "Nada tocando",
                    body = "Abra um app de música ou conecte o celular por Bluetooth.",
                    button = music?.app?.let { "Abrir ${it.label}" },
                    onClick = { music?.app?.let(actions::launch) },
                )
            }
            is MediaState.Active -> ActiveMedia(s.now, vm, actions)
        }
    }
}

@Composable
private fun MediaMessage(title: String, body: String, button: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_music), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null) {
            Spacer(Modifier.height(14.dp))
            PillButton(button, onClick, primary = true)
        }
    }
}

@Composable
private fun ActiveMedia(np: NowPlaying, vm: LauncherViewModel, actions: LauncherActions) {
    val cs = MaterialTheme.colorScheme
    val compact = LocalCompact.current
    Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).tap(onClick = actions::openPlayer),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(if (LocalCompact.current) 64.dp else 92.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val art = np.art
                if (art != null) {
                    Image(art, contentDescription = "Capa", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(painterResource(R.drawable.ic_music), null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(np.appLabel.uppercase(PtBr), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = cs.primary, maxLines = 1)
                Text(
                    np.title.ifBlank { "Sem título" },
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (np.artist.isNotBlank()) {
                    Text(np.artist, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (!LocalReduceMotion.current && np.durationMs > 0) {
            Progress(np)
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ic_previous, "Anterior", { vm.c.media.previous() }, enabled = np.canPrevious)
            RoundIconButton(
                if (np.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (np.isPlaying) "Pausar" else "Tocar",
                { vm.c.media.playPause() },
                size = if (compact) 64.dp else 76.dp,
                filled = true,
            )
            RoundIconButton(R.drawable.ic_next, "Próxima", { vm.c.media.next() }, enabled = np.canNext)
        }
    }
}

/** Barra de progresso que se atualiza 1x/s só enquanto toca (recomposição isolada aqui). */
@Composable
private fun Progress(np: NowPlaying) {
    val pos by produceState(np.positionNow(), np) {
        while (true) {
            value = np.positionNow(SystemClock.elapsedRealtime())
            if (!np.isPlaying) break
            delay(1_000)
        }
    }
    Column {
        LinearProgressIndicator(
            progress = { (pos.toFloat() / np.durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.15f),
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmt(pos), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(fmt(np.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---------------- Atalhos ----------------

@Composable
private fun ShortcutGrid(vm: LauncherViewModel, actions: LauncherActions, modifier: Modifier) {
    val items by vm.shortcuts.collectAsStateWithLifecycle()
    val gap = if (LocalCompact.current) 8.dp else 10.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { ShortcutTile(it, vm, actions, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(item: ResolvedShortcut, vm: LauncherViewModel, actions: LauncherActions, modifier: Modifier) {
    val app = item.app
    val compact = LocalCompact.current
    val pick = { vm.openPicker(PickTarget.Shortcut(item.slot)) }
    GlassCard(modifier) {
        Column(
            Modifier.fillMaxSize().tap(onLongClick = pick) { if (app != null) actions.launch(app) else pick() }.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (app != null) {
                AppIcon(vm.c.apps, app, if (compact) 32.dp else 44.dp)
            } else {
                Icon(painterResource(item.slot.icon), null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(if (compact) 26.dp else 36.dp))
            }
            Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
            Text(
                if (app != null && !item.automatic) app.label else item.slot.label,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (app != null) Color.White else Color.White.copy(alpha = 0.55f),
            )
            if (app == null && !compact) Text("Toque para escolher", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

// ---------------- Dock ----------------

@Composable
private fun Dock(vm: LauncherViewModel, actions: LauncherActions) {
    val headunit by vm.headunit.collectAsStateWithLifecycle()
    val size = if (LocalCompact.current) 64.dp else 72.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(R.drawable.ic_apps, "Todos os apps", { vm.open(Screen.Apps) }, size = size)
        Spacer(Modifier.width(18.dp))
        AndroidAutoButton(
            height = size,
            subtitle = if (headunit != null) "Conecte o celular no USB" else "Toque para instalar",
            onClick = { headunit?.let(actions::launch) ?: vm.openHeadunitHelp(fromSettings = false) },
            onLongClick = { vm.openHeadunitHelp(fromSettings = false) },
        )
        Spacer(Modifier.width(18.dp))
        RoundIconButton(R.drawable.ic_settings, "Configurações", { vm.open(Screen.Settings) }, size = size)
    }
}

@Composable
private fun AndroidAutoButton(height: androidx.compose.ui.unit.Dp, subtitle: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .width(340.dp)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Brush.horizontalGradient(listOf(cs.primary, cs.primaryContainer)))
            .tap(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_car), null, tint = Color.White, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Android Auto", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
