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
import android.util.LruCache
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

/** Uma linha do "Diagnóstico de mídia": o que cada sessão ativa está publicando. */
data class SessionInfo(
    val packageName: String,
    val appLabel: String,
    val state: Int,
    val title: String,
    val artist: String,
    val hasArt: Boolean,
    /** É a sessão mostrada no cartão "Tocando agora". */
    val chosen: Boolean,
)

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
    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    /** Todas as sessões ativas, para a tela de diagnóstico. */
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    /** Em ordem de prioridade do sistema (a mais recente primeiro). */
    private var tracked: List<Tracked> = emptyList()
    private val byToken = HashMap<MediaSession.Token, Tracked>()
    private var current: Tracked? = null

    private var artKey: Any? = null
    private var artJob: Job? = null
    /** Capa já reduzida, por faixa: voltar a uma faixa ou trocar de player não decodifica de novo. */
    private val artCache = LruCache<Any, ImageBitmap>(ART_CACHE_TRACKS)
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
        tracked.forEach { it.controller.unregisterCallback(it) }
        byToken.clear()
        tracked = emptyList()
        current = null
        _sessions.value = emptyList()
    }

    private fun onSessions(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        tracked.filter { it.controller.sessionToken !in tokens }.forEach { t ->
            byToken.remove(t.controller.sessionToken)
            t.controller.unregisterCallback(t)
        }
        tracked = list.map { c ->
            byToken.getOrPut(c.sessionToken) { Tracked(c).also { c.registerCallback(it, main) } }
        }
        pick()
    }

    /**
     * Guarda o último metadado/estado recebido pelo callback. MediaController.getMetadata() é uma
     * chamada Binder que copia a capa inteira a cada vez; o Open Headunit atualiza o estado a cada
     * segundo, então ler de novo a cada mudança custaria ~2 MB por segundo na central.
     */
    private inner class Tracked(val controller: MediaController) : MediaController.Callback() {
        var metadata: MediaMetadata? = controller.metadata
        var playback: PlaybackState? = controller.playbackState

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playback = state
            pick()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            this.metadata = metadata
            pick()
        }

        override fun onSessionDestroyed() = pick()
    }

    /**
     * Prioridade: quem está tocando > a sessão atual > a mais recente com metadados.
     * Empate entre duas tocando: a lista do sistema já vem com a mais recente primeiro.
     */
    private fun pick() {
        if (!started) return
        val list = tracked
        val chosen = list.firstOrNull { it.playback.isActive() }
            ?: current?.takeIf { it in list }
            ?: list.firstOrNull { it.metadata != null }
        current = chosen
        _sessions.value = list.map { t ->
            val m = t.metadata
            SessionInfo(
                packageName = t.controller.packageName,
                appLabel = label(t.controller.packageName),
                state = t.playback?.state ?: PlaybackState.STATE_NONE,
                title = m?.let(::titleOf).orEmpty(),
                artist = m?.let(::artistOf).orEmpty(),
                hasArt = m != null && (artBitmapOf(m) != null || artUriOf(m) != null),
                chosen = t === chosen,
            )
        }
        if (chosen == null) {
            _state.value = MediaState.Idle
            return
        }
        publish(chosen)
    }

    private fun publish(t: Tracked) {
        val meta = t.metadata
        val ps = t.playback
        val pkg = t.controller.packageName
        val actions = ps?.actions ?: 0L
        // actions == 0: app não declara; mostramos os botões e deixamos o app decidir.
        val unknown = actions == 0L
        val now = NowPlaying(
            packageName = pkg,
            appLabel = label(pkg),
            title = meta?.let(::titleOf).orEmpty(),
            artist = meta?.let(::artistOf).orEmpty(),
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
        val cached = artCache.get(key)
        if (key == artKey || cached != null) {
            if (key != artKey) {
                artJob?.cancel()
                artKey = key
            }
            _state.value = MediaState.Active(now.copy(art = cached))
            return
        }
        // Música nova: mostra sem capa e decodifica fora da thread principal.
        artKey = key
        _state.value = MediaState.Active(now)
        artJob?.cancel()
        if (meta == null) return
        val max = artMaxPx
        artJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { loadArt(meta, max) }.getOrNull() } ?: return@launch
            val img = bmp.asImageBitmap()
            artCache.put(key, img)
            if (artKey != key) return@launch
            val s = _state.value
            if (s is MediaState.Active && s.now.packageName == pkg) _state.value = MediaState.Active(s.now.copy(art = img))
        }
    }

    /**
     * Identifica a faixa sem olhar a identidade do Bitmap: cada leitura via Binder cria um objeto
     * novo, e a chave mudaria a cada atualização de estado (capa piscando e decodificada de novo).
     */
    private fun artKeyOf(pkg: String, meta: MediaMetadata?): Any {
        if (meta == null) return pkg
        val bmp = artBitmapOf(meta)
        return listOf(
            pkg,
            artMaxPx,
            meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            meta.getString(MediaMetadata.METADATA_KEY_TITLE),
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM),
            bmp?.let { "${it.width}x${it.height}" },
            artUriOf(meta),
        )
    }

    private fun titleOf(meta: MediaMetadata): String =
        meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

    private fun artistOf(meta: MediaMetadata): String =
        meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

    /** Ordem pedida: ALBUM_ART → ART → DISPLAY_ICON. */
    private fun artBitmapOf(meta: MediaMetadata): Bitmap? =
        meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun artUriOf(meta: MediaMetadata): String? =
        meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

    /**
     * A capa que vem como Bitmap no metadado já chegou decodificada pelo Binder (o Open Headunit
     * limita a 720 px); aqui só é reduzida. inSampleSize vale para capas por URI local.
     */
    private fun loadArt(meta: MediaMetadata, max: Int): Bitmap? {
        artBitmapOf(meta)?.let { return scaleDown(it, max) }
        val raw = artUriOf(meta) ?: return null
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

    fun trimMemory() = artCache.evictAll()

    /** Só na build de debug (prints/manual): mostra uma música fictícia. O R8 remove do release. */
    fun showDemo(now: NowPlaying) {
        stop()
        _state.value = MediaState.Active(now)
    }

    // ---- Controles ----

    /**
     * transportControls.play()/pause(). Só usa a tecla PLAY_PAUSE se o player declarar apenas ela
     * (alguns apps antigos). O Open Headunit declara PLAY/PAUSE e repassa ao celular como tecla.
     */
    fun playPause() {
        val t = current ?: return
        val c = t.controller
        val playing = t.playback.isActive()
        val actions = t.playback?.actions ?: 0L
        val wanted = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        when {
            actions != 0L && actions and wanted == 0L && actions and PlaybackState.ACTION_PLAY_PAUSE != 0L -> {
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }
            playing -> c.transportControls.pause()
            else -> c.transportControls.play()
        }
    }

    fun next() = current?.controller?.transportControls?.skipToNext()
    fun previous() = current?.controller?.transportControls?.skipToPrevious()

    /** Abre a tela do player que está tocando. */
    fun openPlayer(): Boolean {
        val pi = current?.controller?.sessionActivity ?: return false
        return runCatching { pi.send(); true }.getOrDefault(false)
    }

    private fun PlaybackState?.isActive(): Boolean = when (this?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private companion object {
        /** Até 256 px cada (~256 KB): 6 faixas cabem em ~1,5 MB. */
        const val ART_CACHE_TRACKS = 6
    }
}
