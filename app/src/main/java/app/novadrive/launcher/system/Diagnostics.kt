package app.novadrive.launcher.system

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log

/** Medições para validar o orçamento: abertura < 1,5 s e RAM < 120 MB. */
object Diagnostics {
    const val TAG = "NovaDrive"

    /** Tempo do início do processo até o primeiro frame do HOME (só na abertura a frio). */
    var coldStartMs: Long? = null
        private set

    fun markFirstFrame() {
        if (coldStartMs != null) return
        val ms = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        coldStartMs = ms
        Log.i(TAG, "Abertura a frio até o 1º frame: $ms ms")
    }

    /** PSS do próprio processo, em MB (métrica usada pelo Android para o uso real de RAM). */
    fun pssMb(): Int = (Debug.getPss() / 1024).toInt()

    data class SystemMemory(val availMb: Long, val totalMb: Long, val low: Boolean, val appLimitMb: Int)

    fun systemMemory(context: Context): SystemMemory {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return SystemMemory(info.availMem shr 20, info.totalMem shr 20, info.lowMemory, am.memoryClass)
    }
}
