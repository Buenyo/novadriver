package app.novadrive.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import app.novadrive.launcher.system.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MediaState {
    /** Usuário ainda não liberou o acesso a notificações. */
    data object NoPermission : MediaState
    /** Permissão ok, mas nenhum player com sessão ativa. */
    data object Idle : MediaState
    data class Active(val now: NowPlaying) : MediaState
}

data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() da última posição informada pelo player. */
    val positionAt: Long,
    val speed: Float,
) {
    fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionAt <= 0) return positionMs
        val p = positionMs + ((now - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Lê as sessões de mídia de QUALQUER app (Spotify, YouTube Music, rádio, Bluetooth...)
 * e escolhe a que está tocando. Só fica ativo com o HOME visível (start/stop).
 */
class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<MediaState>(MediaState.Idle)
    val state: StateFlow<MediaState> = _state.asStateFlow()

    private var started = false
    /** Em ordem de prioridade do sistema (a mais recente primeiro). */
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private var current: MediaController? = null

    private var artKey: Any? = null
    private var art: ImageBitmap? = null
    private var artJob: Job? = null
    private val labels = HashMap<String, String>()

    /** Menor no modo performance: menos memória e decodificação mais rápida. */
    var artMaxPx: Int = 256

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list.orEmpty())
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        if (started) return
        started = true
        attach()
    }

    fun stop() {
        started = false
        detach()
    }

    /** Chamado pelo serviço quando o sistema o conecta (ex.: logo após conceder a permissão). */
    fun onListenerConnected() {
        if (!started) return
        detach()
        attach()
    }

    /** Revalida depois de voltar da tela de permissões. */
    fun recheck() {
        if (started && (_state.value == MediaState.NoPermission) == hasAccess()) {
            detach()
            attach()
        }
    }

    private fun attach() {
        if (!hasAccess()) {
            _state.value = MediaState.NoPermission
            return
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            onSessions(msm.getActiveSessions(listener))
        } catch (e: SecurityException) {
            // Permissão listada mas serviço ainda não conectado; onListenerConnected tenta de novo.
            Log.w(Diagnostics.TAG, "getActiveSessions negado", e)
            _state.value = MediaState.NoPermission
        }
    }

    private fun detach() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { c -> callbacks[c.sessionToken]?.let(c::unregisterCallback) }
        callbacks.clear()
        controllers = emptyList()
        current = null
    }

    private fun onSessions(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        controllers.filter { it.sessionToken !in tokens }.forEach { c ->
            callbacks.remove(c.sessionToken)?.let(c::unregisterCallback)
        }
        list.forEach { c ->
            if (c.sessionToken !in callbacks) {
                val cb = ControllerCallback()
                c.registerCallback(cb, main)
                callbacks[c.sessionToken] = cb
            }
        }
        controllers = list
        pick()
    }

    private inner class ControllerCallback : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = pick()
    }

    /** Prioridade: quem está tocando > a sessão atual > a mais recente com metadados. */
    private fun pick() {
        if (!started) return
        val list = controllers
        val chosen = list.firstOrNull { it.playbackState.isActive() }
            ?: current?.let { cur -> list.firstOrNull { it.sessionToken == cur.sessionToken } }
            ?: list.firstOrNull { it.metadata != null }
        current = chosen
        if (chosen == null) {
            _state.value = MediaState.Idle
            return
        }
        publish(chosen)
    }

    private fun publish(c: MediaController) {
        val meta = c.metadata
        val ps = c.playbackState
        val pkg = c.packageName
        val actions = ps?.actions ?: 0L
        // actions == 0: app não declara; mostramos os botões e deixamos o app decidir.
        val unknown = actions == 0L
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val now = NowPlaying(
            packageName = pkg,
            appLabel = label(pkg),
            title = title,
            artist = artist,
            art = null,
            isPlaying = ps.isActive(),
            canPrevious = unknown || actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = unknown || actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
            positionMs = ps?.position?.takeIf { it >= 0 } ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
        val key = artKeyOf(pkg, meta)
        if (key == artKey) {
            _state.value = MediaState.Active(now.copy(art = art))
            return
        }
        // Música nova: mostra sem capa e decodifica fora da thread principal.
        artKey = key
        art = null
        _state.value = MediaState.Active(now)
        artJob?.cancel()
        if (meta == null) return
        artJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { loadArt(meta) }.getOrNull() }
            if (artKey != key) return@launch
            art = bmp?.asImageBitmap()
            val s = _state.value
            if (s is MediaState.Active && s.now.packageName == pkg) _state.value = MediaState.Active(s.now.copy(art = art))
        }
    }

    private fun artKeyOf(pkg: String, meta: MediaMetadata?): Any {
        if (meta == null) return pkg
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
        return listOf(
            pkg,
            meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            meta.getString(MediaMetadata.METADATA_KEY_TITLE),
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
            bmp?.let { System.identityHashCode(it) },
            uri,
        )
    }

    private fun loadArt(meta: MediaMetadata): Bitmap? {
        val max = artMaxPx
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) return scaleDown(bmp, max)
        val raw = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null
        val uri = Uri.parse(raw)
        // Só fontes locais: nada de baixar imagens da internet em segundo plano.
        if (uri.scheme !in setOf("content", "android.resource", "file")) return null
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return scaleDown(decoded, max)
    }

    private fun scaleDown(src: Bitmap, max: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= max && h <= max) return src
        val f = max.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1), true)
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    // ---- Controles ----

    fun playPause() {
        val c = current ?: return
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        when {
            actions and PlaybackState.ACTION_PLAY_PAUSE != 0L -> {
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }
            ps.isActive() -> c.transportControls.pause()
            else -> c.transportControls.play()
        }
    }

    fun next() = current?.transportControls?.skipToNext()
    fun previous() = current?.transportControls?.skipToPrevious()

    /** Abre a tela do player que está tocando. */
    fun openPlayer(): Boolean {
        val pi = current?.sessionActivity ?: return false
        return runCatching { pi.send(); true }.getOrDefault(false)
    }

    private fun PlaybackState?.isActive(): Boolean = when (this?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }
}
