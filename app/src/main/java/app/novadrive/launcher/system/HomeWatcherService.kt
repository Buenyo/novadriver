package app.novadrive.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.novadrive.launcher.NovaDriveApp

/**
 * "Vigia da tela inicial": quando o launcher do fabricante vem para a frente (o botão HOME desta
 * central o abre mesmo com o NovaDrive como padrão), traz o NovaDrive de volta.
 *
 * Só recebe TYPE_WINDOW_STATE_CHANGED e lê apenas o nome do pacote do evento: não lê o conteúdo
 * das janelas (canRetrieveWindowContent desligado).
 */
class HomeWatcherService : AccessibilityService() {
    private val container get() = (application as NovaDriveApp).container
    private var oem: Set<String> = emptySet()
    private var oemAt = 0L
    private var lastJump = 0L

    override fun onServiceConnected() {
        HomeWatcher.setConnected(true)
        refreshOem(force = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        refreshOem(force = false)
        val now = SystemClock.elapsedRealtime()
        if (!HomeWatcher.onWindow(pkg, oem, container.settings.current.replaceOemHome, now, lastJump)) return
        // NovaDrive em modo de segurança: devolver o usuário a ele criaria uma armadilha.
        if (CrashGuard.isLooping(this)) return
        lastJump = now
        try {
            startActivity(HomeWatcher.homeIntent(this))
        } catch (e: RuntimeException) {
            Log.w(Diagnostics.TAG, "Vigia: não abriu o NovaDrive", e)
        }
    }

    /** Apps com MAIN + HOME, menos o NovaDrive e as telas de emergência do sistema. Sem pacote fixo. */
    private fun refreshOem(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - oemAt < OEM_REFRESH_MS) return
        oemAt = now
        oem = container.apps.otherHomeApps().mapTo(HashSet()) { it.packageName }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        HomeWatcher.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        HomeWatcher.setConnected(false)
        super.onDestroy()
    }

    private companion object {
        const val OEM_REFRESH_MS = 60_000L
    }
}
