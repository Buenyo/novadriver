package app.novadrive.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val searchKey: String,
) {
    val packageName: String get() = component.packageName
    val key: String get() = component.flattenToShortString()
}

class AppsRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val ownPackage = context.packageName

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    /** Apps com ícone na gaveta, em ordem alfabética pt-BR. null = carregando. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    @Volatile private var infos: Map<ComponentName, LauncherActivityInfo> = emptyMap()
    private var loadJob: Job? = null

    // Ícones já desenhados no tamanho final; limite por bytes para caber no orçamento de RAM.
    private val icons = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun changed(packageName: String) {
        icons.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(icons::remove)
        refresh()
    }

    /** Recarrega a lista; várias mudanças seguidas (instalação/atualização) viram uma leitura só. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (_apps.value != null) delay(300)
            val (list, map) = withContext(Dispatchers.IO) { load() }
            infos = map
            _apps.value = list
        }
    }

    private fun load(): Pair<List<AppEntry>, Map<ComponentName, LauncherActivityInfo>> {
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }
        val activities = launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != ownPackage }
        val list = activities
            .map { AppEntry(it.componentName, it.label.toString().trim(), SearchText.normalize(it.label.toString())) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        return list to activities.associateBy { it.componentName }
    }

    /** Ícone desenhado em [sizePx]; leitura e desenho fora da thread principal. */
    suspend fun icon(component: ComponentName, sizePx: Int): ImageBitmap? {
        val key = "${component.flattenToShortString()}@$sizePx"
        icons.get(key)?.let { return it.asImageBitmap() }
        val info = infos[component] ?: return null
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val d = info.getIcon(context.resources.displayMetrics.densityDpi)
                Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
                    d.setBounds(0, 0, sizePx, sizePx)
                    d.draw(Canvas(it))
                }
            }.getOrNull()
        } ?: return null
        icons.put(key, bmp)
        return bmp.asImageBitmap()
    }

    fun find(component: String?): AppEntry? {
        if (component == null) return null
        val cn = ComponentName.unflattenFromString(component) ?: return null
        return _apps.value?.firstOrNull { it.component == cn }
    }

    /** Outras telas iniciais instaladas (o launcher original da central). Caminho de volta. */
    fun otherHomeApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, 0)
            // priority < 0: telas de emergência do sistema (ex.: Settings FallbackHome), não são launchers.
            .filter { it.activityInfo.packageName != ownPackage && it.priority >= 0 }
            .map {
                val label = it.loadLabel(pm).toString()
                AppEntry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), label, SearchText.normalize(label))
            }
    }

    fun trimMemory() = icons.evictAll()

    private companion object {
        const val ICON_CACHE_BYTES = 6 * 1024 * 1024
    }
}

object SearchText {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        marks.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()

    /** Todas as palavras digitadas precisam aparecer no nome (sem acento, sem caixa). */
    fun matches(entry: AppEntry, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        return words.all { entry.searchKey.contains(it) || entry.packageName.contains(it) }
    }
}
