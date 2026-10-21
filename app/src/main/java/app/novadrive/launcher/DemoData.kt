package app.novadrive.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.ui.graphics.asImageBitmap
import app.novadrive.launcher.media.NowPlaying

/** Dados fictícios para prints e manual (build de debug). Nunca usado no release. */
object DemoData {
    var active = false
        private set

    fun apply(activity: MainActivity, c: AppContainer) {
        active = true
        val art = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bmp ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFE5132D.toInt(), 0xFF1A0508.toInt(), Shader.TileMode.CLAMP)
            }
            Canvas(bmp).apply {
                drawRect(0f, 0f, 256f, 256f, p)
                drawCircle(128f, 128f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
                drawCircle(128f, 128f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0506.toInt() })
            }
        }
        c.media.showDemo(
            NowPlaying(
                packageName = activity.packageName,
                appLabel = "Spotify",
                title = "Estrada ao Entardecer",
                artist = "Demonstração NovaDrive",
                art = art.asImageBitmap(),
                isPlaying = true,
                canPrevious = true,
                canNext = true,
                durationMs = 214_000,
                positionMs = 83_000,
                positionAt = SystemClock.elapsedRealtime(),
                speed = 1f,
            ),
        )
        c.weather.start()
    }
}
