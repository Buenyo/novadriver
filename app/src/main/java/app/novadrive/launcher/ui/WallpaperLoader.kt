package app.novadrive.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.novadrive.launcher.R
import app.novadrive.launcher.settings.WallpaperChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodifica o papel de parede UMA vez, já recortado no tamanho exato da tela (1:1 com os pixels):
 * a GPU Mali da T3 desenha sem escalar e a RAM fica em ~2,4 MB para 1024x600.
 */
class WallpaperLoader(private val context: Context) {
    private var cacheKey: Triple<WallpaperChoice, Int, Int>? = null
    private var cached: ImageBitmap? = null

    val brandingPresent: Boolean get() = context.resources.getBoolean(R.bool.branding_present)

    suspend fun load(choice: WallpaperChoice, width: Int, height: Int): ImageBitmap? {
        if (width <= 0 || height <= 0) return null
        val key = Triple(choice, width, height)
        if (key == cacheKey) return cached
        val res = resFor(choice) ?: return null
        val bmp = withContext(Dispatchers.IO) { runCatching { decodeCropped(res, width, height) }.getOrNull() }
        cacheKey = key
        cached = bmp?.asImageBitmap()
        return cached
    }

    fun clear() {
        cacheKey = null
        cached = null
    }

    @DrawableRes
    private fun resFor(choice: WallpaperChoice): Int? {
        val id = when (choice) {
            WallpaperChoice.REAR -> R.drawable.wallpaper_rear
            WallpaperChoice.STUDIO -> R.drawable.wallpaper_studio
            WallpaperChoice.NONE -> return null
        }
        // Sem a pasta branding/ o recurso é um gradiente XML: a UI desenha o fundo genérico.
        val tv = TypedValue()
        context.resources.getValue(id, tv, true)
        return if (tv.string?.endsWith(".xml") == true) null else id
    }

    private fun decodeCropped(@DrawableRes id: Int, w: Int, h: Int): Bitmap? {
        val r = context.resources
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(r, id, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val src = BitmapFactory.decodeResource(r, id, BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }) ?: return null
        // Center-crop para preencher a tela sem distorcer.
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).toInt().coerceAtMost(src.width)
        val sh = (h / scale).toInt().coerceAtMost(src.height)
        val x = (src.width - sw) / 2
        val y = (src.height - sh) / 2
        val cropped = Bitmap.createBitmap(src, x, y, sw, sh)
        val out = if (sw == w && sh == h) cropped else Bitmap.createScaledBitmap(cropped, w, h, true)
        if (cropped !== src && out !== cropped) cropped.recycle()
        if (out !== src) src.recycle()
        return out
    }
}
