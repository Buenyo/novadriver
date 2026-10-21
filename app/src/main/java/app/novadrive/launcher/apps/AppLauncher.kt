package app.novadrive.launcher.apps

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import app.novadrive.launcher.system.Diagnostics

/** Abre apps e telas do sistema com tratamento de erro (firmwares de central removem telas). */
class AppLauncher(private val context: Context) {

    fun launch(activity: Activity, component: ComponentName, noAnimation: Boolean): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(activity, intent, noAnimation, "Não foi possível abrir o app")
    }

    fun launchPackage(activity: Activity, packageName: String, noAnimation: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false.also { toast("App não instalado") }
        return start(activity, intent, noAnimation, "Não foi possível abrir o app")
    }

    fun openNotificationAccess(activity: Activity): Boolean =
        tryStart(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) ||
            tryStart(activity, Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")) ||
            openAppInfo(activity, context.packageName)

    fun openAppInfo(activity: Activity, packageName: String): Boolean =
        tryStart(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    /** Tela "App de início padrão". Se o firmware removeu, cai nas configurações gerais. */
    fun openHomeSettings(activity: Activity): Boolean =
        tryStart(activity, Intent(Settings.ACTION_HOME_SETTINGS)) || openSystemSettings(activity)

    fun openSystemSettings(activity: Activity): Boolean =
        tryStart(activity, Intent(Settings.ACTION_SETTINGS)).also { if (!it) toast("Configurações indisponíveis") }

    fun openUnknownSources(activity: Activity): Boolean =
        tryStart(activity, Intent(Settings.ACTION_SECURITY_SETTINGS)) || openSystemSettings(activity)

    fun openUrl(activity: Activity, url: String): Boolean =
        tryStart(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url))).also { if (!it) toast("Nenhum navegador instalado") }

    /** Abre o launcher original explicitamente, sem mudar o padrão. */
    fun openHome(activity: Activity, component: ComponentName): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(activity, intent, false, "Não foi possível abrir o launcher")
    }

    private fun start(activity: Activity, intent: Intent, noAnimation: Boolean, error: String): Boolean {
        val options = if (noAnimation) ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle() else null
        return try {
            activity.startActivity(intent, options)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(Diagnostics.TAG, "Activity não encontrada: ${intent.component ?: intent.action}")
            toast(error); false
        } catch (e: SecurityException) {
            Log.w(Diagnostics.TAG, "Sem permissão para abrir: ${intent.component}", e)
            toast(error); false
        }
    }

    private fun tryStart(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
