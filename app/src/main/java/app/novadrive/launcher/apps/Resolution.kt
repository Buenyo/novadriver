package app.novadrive.launcher.apps

import android.content.ComponentName
import app.novadrive.launcher.settings.ShortcutSlot

data class ResolvedShortcut(val slot: ShortcutSlot, val app: AppEntry?, val automatic: Boolean)

/** Regras puras (testáveis na JVM) de qual app cada botão abre. */
object Resolution {
    /**
     * Open Headunit: applicationId lido no build.gradle.kts do repositório oficial
     * (andreknieriem/open-headunit, flavors github e playstore usam o mesmo ID).
     * Como versões/forks podem mudar, também aceitamos qualquer app cujo pacote ou nome
     * contenha "headunit". O usuário sempre pode escolher outro app manualmente.
     */
    const val OPEN_HEADUNIT_PACKAGE = "com.andrerinas.headunitrevived"
    private val headunitHint = Regex("""(?i)head\s*unit|open\s*headunit""")

    fun shortcut(slot: ShortcutSlot, override: String?, apps: List<AppEntry>): ResolvedShortcut {
        override?.let { o ->
            val cn = ComponentName.unflattenFromString(o)
            apps.firstOrNull { it.component == cn }?.let { return ResolvedShortcut(slot, it, automatic = false) }
        }
        val known = slot.knownPackages.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        val hinted = known ?: slot.labelHint?.let { re -> apps.firstOrNull { re.containsMatchIn(it.label) } }
        return ResolvedShortcut(slot, hinted, automatic = true)
    }

    fun headunit(override: String?, apps: List<AppEntry>): AppEntry? {
        override?.let { o ->
            val cn = ComponentName.unflattenFromString(o)
            apps.firstOrNull { it.component == cn }?.let { return it }
        }
        return apps.firstOrNull { it.packageName == OPEN_HEADUNIT_PACKAGE }
            ?: apps.firstOrNull { it.packageName.contains("headunit", ignoreCase = true) }
            ?: apps.firstOrNull { headunitHint.containsMatchIn(it.label) }
    }
}
