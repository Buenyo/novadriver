package app.novadrive.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.novadrive.launcher.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado do "Vigia da tela inicial", compartilhado entre o serviço de acessibilidade e a UI
 * (rodam no mesmo processo). A regra de decisão é pura para ser testada na JVM.
 */
object HomeWatcher {
    const val DEBOUNCE_MS = 500L

    private val _connected = MutableStateFlow(false)
    /** O sistema ligou o serviço (o usuário ativou em Acessibilidade). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Launcher aberto de propósito pelo NovaDrive (caminho de volta): não é devolvido. */
    @Volatile private var bypass: String? = null

    internal fun setConnected(v: Boolean) {
        _connected.value = v
    }

    /** Chamado antes de abrir outro launcher pelo NovaDrive. Vale até outro app vir para a frente. */
    fun allow(packageName: String) {
        bypass = packageName
    }

    /**
     * true = trazer o NovaDrive para a frente agora. Também atualiza o desvio: qualquer janela de
     * outro pacote encerra a liberação dada por [allow].
     */
    fun onWindow(packageName: String, oem: Collection<String>, enabled: Boolean, now: Long, lastJump: Long): Boolean {
        val b = bypass
        if (b != null) {
            if (b == packageName) return false
            bypass = null
        }
        return shouldBounce(packageName, oem, enabled, now, lastJump)
    }

    fun shouldBounce(packageName: String, oem: Collection<String>, enabled: Boolean, now: Long, lastJump: Long): Boolean =
        enabled && packageName in oem && now - lastJump >= DEBOUNCE_MS

    /** Mesma intent do botão HOME: a MainActivity volta para a tela inicial (onNewIntent). */
    fun homeIntent(context: Context): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    /** Lê a lista do sistema; vale mesmo se o serviço ainda não conectou nesta execução. */
    fun isEnabledInSystem(context: Context): Boolean {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, HomeWatcherService::class.java)
        return raw.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
