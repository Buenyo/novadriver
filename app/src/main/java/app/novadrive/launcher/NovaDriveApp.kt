package app.novadrive.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import app.novadrive.launcher.apps.AppLauncher
import app.novadrive.launcher.apps.AppsRepository
import app.novadrive.launcher.media.MediaRepository
import app.novadrive.launcher.settings.SettingsRepository
import app.novadrive.launcher.system.CrashGuard
import app.novadrive.launcher.system.clockFlow
import app.novadrive.launcher.ui.WallpaperLoader
import app.novadrive.launcher.weather.LocationSource
import app.novadrive.launcher.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NovaDriveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.trimMemory()
    }
}

/** DI manual: um único lugar cria e liga os repositórios. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(app, scope)
    val apps = AppsRepository(app, scope)
    val media = MediaRepository(app, scope)
    val location = LocationSource(app)
    val weather = WeatherRepository(app, settings, location, scope)
    val launcher = AppLauncher(app)
    val wallpaper = WallpaperLoader(app)

    /** Atualiza a cada minuto (ACTION_TIME_TICK), só enquanto alguém observa. */
    val clock: StateFlow<Long> = clockFlow(app)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /** HOME visível: liga mídia e clima. Nada disso roda com a tela de outro app aberta. */
    fun onForeground() {
        media.start()
        weather.start()
    }

    fun onBackground() {
        media.stop()
        weather.stop()
    }

    fun trimMemory() {
        apps.trimMemory()
        wallpaper.clear()
    }
}
