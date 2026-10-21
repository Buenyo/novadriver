package app.novadrive.launcher

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.novadrive.launcher.apps.AppEntry
import app.novadrive.launcher.system.CrashGuard
import app.novadrive.launcher.system.Diagnostics
import app.novadrive.launcher.ui.LauncherActions
import app.novadrive.launcher.ui.LauncherViewModel
import app.novadrive.launcher.ui.NovaDriveRoot

class MainActivity : ComponentActivity(), LauncherActions {
    private val container get() = (application as NovaDriveApp).container
    private val vm: LauncherViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(container) as T
        }
    }
    private var safeMode = false

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.weather.refreshNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashGuard.isLooping(this)) {
            safeMode = true
            setContentView(buildSafeMode())
            return
        }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEMO, false) == true) DemoData.apply(this, container)
        setContent {
            NovaDriveRoot(vm, this) {
                Diagnostics.markFirstFrame()
                reportFullyDrawn()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!safeMode && !DemoData.active) container.onForeground()
    }

    override fun onResume() {
        super.onResume()
        if (!safeMode) vm.onResume()
    }

    override fun onStop() {
        if (!safeMode) container.onBackground()
        super.onStop()
    }

    /** Botão HOME com o launcher já aberto: volta para a tela inicial. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!safeMode && intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    private val noAnim get() = container.settings.current.performanceMode

    // ---- LauncherActions ----
    override fun launch(app: AppEntry) {
        container.launcher.launch(this, app.component, noAnim)
    }

    override fun launchPackage(packageName: String) {
        container.launcher.launchPackage(this, packageName, noAnim)
    }

    override fun openAppInfo(app: AppEntry) {
        container.launcher.openAppInfo(this, app.packageName)
    }

    override fun openNotificationAccess() {
        container.launcher.openNotificationAccess(this)
    }

    override fun openHomeSettings() {
        container.launcher.openHomeSettings(this)
    }

    override fun openSystemSettings() {
        container.launcher.openSystemSettings(this)
    }

    override fun openAccessibilitySettings() {
        container.launcher.openAccessibilitySettings(this)
    }

    override fun openUnknownSources() {
        container.launcher.openUnknownSources(this)
    }

    override fun openUrl(url: String) {
        container.launcher.openUrl(this, url)
    }

    override fun openOtherHome(app: AppEntry) {
        container.launcher.openHome(this, app.component)
    }

    override fun requestLocationPermission() {
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun openPlayer() {
        if (container.media.openPlayer()) return
        (container.media.state.value as? app.novadrive.launcher.media.MediaState.Active)?.now?.packageName?.let(::launchPackage)
    }

    // ---- Modo de segurança (sem Compose, para funcionar mesmo se a UI principal estiver quebrando) ----
    private fun buildSafeMode(): View {
        val pad = dp(24)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(t: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = t
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setPadding(0, dp(6), 0, dp(6))
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            minHeight = dp(72)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnClickListener { onClick() }
        }
        col.addView(text("NovaDrive — modo de segurança", 28f))
        col.addView(text("O NovaDrive fechou várias vezes seguidas. Use um dos botões para continuar usando a central.", 18f, 0xFFCCCCCC.toInt()))
        container.apps.otherHomeApps().forEach { h ->
            col.addView(button("Abrir ${h.label} (launcher original)") { container.launcher.openHome(this, h.component) })
        }
        col.addView(button("Escolher tela inicial padrão") { container.launcher.openHomeSettings(this) })
        col.addView(button("Configurações do Android") { container.launcher.openSystemSettings(this) })
        col.addView(button("Tentar o NovaDrive de novo") {
            CrashGuard.reset(this)
            recreate()
        })
        return ScrollView(this).apply {
            setBackgroundColor(0xFF0B0506.toInt())
            addView(col)
            col.gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** adb shell am start -n <pkg>/app.novadrive.launcher.MainActivity --ez nd_demo true (só debug). */
        const val EXTRA_DEMO = "nd_demo"
    }
}
