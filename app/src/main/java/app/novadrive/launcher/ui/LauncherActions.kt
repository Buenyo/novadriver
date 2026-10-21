package app.novadrive.launcher.ui

import app.novadrive.launcher.apps.AppEntry

/** Ações que precisam da Activity (abrir apps/telas do sistema, pedir permissão). */
interface LauncherActions {
    fun launch(app: AppEntry)
    fun launchPackage(packageName: String)
    fun openAppInfo(app: AppEntry)
    fun openNotificationAccess()
    fun openHomeSettings()
    fun openSystemSettings()
    fun openAccessibilitySettings()
    fun openUnknownSources()
    fun openUrl(url: String)
    fun openOtherHome(app: AppEntry)
    fun requestLocationPermission()
    fun openPlayer()
}
