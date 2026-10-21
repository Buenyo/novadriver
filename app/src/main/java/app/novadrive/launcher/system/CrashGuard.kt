package app.novadrive.launcher.system

import android.annotation.SuppressLint
import android.content.Context

/**
 * Proteção contra "loop de travamento" do launcher. Se o app quebrar várias vezes seguidas,
 * a central ficaria sem tela inicial; nesse caso a MainActivity abre um modo de segurança
 * (Views simples, sem Compose) com atalhos para o launcher original e as configurações.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val KEY = "times"
    const val WINDOW_MS = 120_000L
    const val LIMIT = 3

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    // commit() síncrono de propósito: o processo vai morrer logo em seguida.
    @SuppressLint("ApplySharedPref")
    private fun record(context: Context, now: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = parse(prefs.getString(KEY, null)) + now
        prefs.edit().putString(KEY, times.takeLast(LIMIT + 2).joinToString(",")).commit()
    }

    fun isLooping(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        shouldEnterSafeMode(parse(prefs(context).getString(KEY, null)), now)

    fun reset(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun shouldEnterSafeMode(times: List<Long>, now: Long): Boolean =
        times.count { now - it in 0..WINDOW_MS } >= LIMIT

    internal fun parse(raw: String?): List<Long> =
        raw?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
