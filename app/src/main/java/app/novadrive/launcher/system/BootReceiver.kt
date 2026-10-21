package app.novadrive.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.novadrive.launcher.NovaDriveApp
import app.novadrive.launcher.settings.Settings
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Abre o NovaDrive ao ligar a central, se "Substituir tela inicial do fabricante" estiver ligada. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val app = context.applicationContext as NovaDriveApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                val s = withTimeoutOrNull(5_000) { app.container.settings.settings.filterNotNull().first() } ?: Settings()
                if (s.replaceOemHome && !CrashGuard.isLooping(app)) app.startActivity(HomeWatcher.homeIntent(app))
            } catch (e: RuntimeException) {
                Log.w(Diagnostics.TAG, "Boot: não abriu o NovaDrive", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** QUICKBOOT_POWERON: "boot rápido" de alguns firmwares, que não mandam BOOT_COMPLETED. */
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON")
    }
}
