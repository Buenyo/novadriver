package app.novadrive.launcher.ui

import android.provider.Settings as SysSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.novadrive.launcher.media.MediaState
import app.novadrive.launcher.ui.drawer.AppDrawerScreen
import app.novadrive.launcher.ui.home.HomeScreen
import app.novadrive.launcher.ui.onboarding.HeadunitHelpScreen
import app.novadrive.launcher.ui.onboarding.NotificationAccessScreen
import app.novadrive.launcher.ui.settings.SettingsScreen
import app.novadrive.launcher.ui.theme.DarkColors
import app.novadrive.launcher.ui.theme.LocalOnWallpaper
import app.novadrive.launcher.ui.theme.NovaDriveTheme
import app.novadrive.launcher.ui.theme.ThemeResolver
import app.novadrive.launcher.ui.theme.motion
import app.novadrive.launcher.weather.WeatherState
import java.util.Calendar

const val OPEN_HEADUNIT_RELEASES = "https://github.com/andreknieriem/open-headunit/releases"

@Composable
fun NovaDriveRoot(vm: LauncherViewModel, actions: LauncherActions, onFirstFrame: () -> Unit) {
    // Até o DataStore responder, a janela mostra só o fundo escuro do tema XML.
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val now by vm.clock.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val w = when (val ws = weather) {
        is WeatherState.Ready -> ws.weather
        is WeatherState.Error -> ws.last
        else -> null
    }
    val minute = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.let { it[Calendar.HOUR_OF_DAY] * 60 + it[Calendar.MINUTE] } }
    val dark = ThemeResolver.isDark(s.theme, isSystemInDarkTheme(), minute, w?.sunriseMin, w?.sunsetMin)
    val systemNoAnim = remember {
        SysSettings.Global.getFloat(ctx.contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val reduce = s.performanceMode || systemNoAnim

    LaunchedEffect(s.performanceMode) { vm.c.media.artMaxPx = if (s.performanceMode) 160 else 256 }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onFirstFrame()
    }
    // Primeira vez sem permissão: mostra a explicação uma vez (depois só pelo cartão).
    LaunchedEffect(media, s.notificationPromptShown) {
        if (!s.notificationPromptShown && media == MediaState.NoPermission) {
            vm.c.settings.setNotificationPromptShown()
            vm.open(Screen.NotificationAccess)
        }
    }
    BackHandler { vm.back() }

    NovaDriveTheme(dark = dark, fontScale = s.fontSize.scale, reduceMotion = reduce) {
        Crossfade(targetState = screen, animationSpec = motion(reduce, 160), label = "screen") { scr ->
            when (scr) {
                // A HOME é sempre escura: foto do carro com fontes claras, em qualquer tema.
                Screen.Home -> MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography) {
                    CompositionLocalProvider(LocalOnWallpaper provides true) {
                        Surface(Modifier.fillMaxSize(), color = DarkColors.background) { HomeScreen(vm, actions, s) }
                    }
                }
                else -> CompositionLocalProvider(LocalOnWallpaper provides false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Secondary(scr, vm, actions, s)
                    }
                }
            }
        }
    }
}

@Composable
private fun Secondary(scr: Screen, vm: LauncherViewModel, actions: LauncherActions, s: app.novadrive.launcher.settings.Settings) {
    val ctx = LocalContext.current
    when (scr) {
        Screen.Home -> Unit
        Screen.Apps -> AppDrawerScreen(vm, onBack = vm::back, onOpen = actions::launch, onLongPress = actions::openAppInfo)
        Screen.Settings -> SettingsScreen(vm, actions, s)
        Screen.NotificationAccess -> NotificationAccessScreen(ctx.packageName, vm::back, actions::openNotificationAccess)
        Screen.HeadunitHelp -> {
            val detected by vm.headunit.collectAsStateWithLifecycle()
            HeadunitHelpScreen(
                detected = detected,
                onBack = vm::back,
                onOpen = actions::launch,
                onPickManually = { vm.openPicker(PickTarget.Headunit, fromHeadunitHelp = true) },
                onOpenReleases = { actions.openUrl(OPEN_HEADUNIT_RELEASES) },
                onUnknownSources = actions::openUnknownSources,
            )
        }
        is Screen.Picker -> {
            val title = when (val t = scr.target) {
                is PickTarget.Shortcut -> "Atalho: ${t.slot.label}"
                PickTarget.Headunit -> "Android Auto"
            }
            AppDrawerScreen(
                vm,
                onBack = vm::back,
                onOpen = { vm.pick(scr.target, it) },
                onLongPress = null,
                pickTitle = title,
                onAutomatic = { vm.pick(scr.target, null) },
            )
        }
    }
}
