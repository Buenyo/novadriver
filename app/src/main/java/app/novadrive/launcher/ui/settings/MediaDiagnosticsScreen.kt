package app.novadrive.launcher.ui.settings

import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.novadrive.launcher.media.MediaState
import app.novadrive.launcher.media.SessionInfo
import app.novadrive.launcher.ui.LauncherViewModel
import app.novadrive.launcher.ui.PillButton
import app.novadrive.launcher.ui.Screen
import app.novadrive.launcher.ui.ScreenHeader

/** Mostra cru o que cada sessão de mídia publica: serve para conferir no carro se o Open Headunit manda a música. */
@Composable
fun MediaDiagnosticsScreen(vm: LauncherViewModel) {
    val sessions by vm.c.media.sessions.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Diagnóstico de mídia", vm::back)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            item {
                Text(
                    "Com o Android Auto tocando, o Open Headunit deve aparecer aqui com o título da música. " +
                        "Se aparecer só \"Android Auto\" e \"Conectado\", ligue no Open Headunit: Configurações → Áudio → " +
                        "\"Faixa atual do AA na sessão de mídia\" (em inglês: \"AA now playing on media session\").",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            when {
                media == MediaState.NoPermission -> item {
                    Text("Sem acesso às notificações: o NovaDrive não consegue ver as sessões.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Permitir acesso", { vm.open(Screen.NotificationAccess) }, primary = true)
                }
                sessions.isEmpty() -> item {
                    Text("Nenhuma sessão de mídia ativa agora.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                }
                else -> items(sessions) { s -> SessionRow(s) }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionInfo) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (s.chosen) cs.primary.copy(alpha = 0.16f) else cs.onSurface.copy(alpha = 0.06f))
            .padding(14.dp),
    ) {
        Text(s.appLabel + if (s.chosen) "  ·  no cartão \"Tocando agora\"" else "", style = MaterialTheme.typography.titleMedium)
        Text(s.packageName, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Field("Estado", stateName(s.state))
        Field("Título", s.title.ifEmpty { "(vazio)" })
        Field("Artista", s.artist.ifEmpty { "(vazio)" })
        Field("Capa", if (s.hasArt) "sim" else "não")
    }
}

@Composable
private fun Field(k: String, v: String) {
    Text("$k: $v", style = MaterialTheme.typography.bodyMedium)
}

private fun stateName(state: Int): String = when (state) {
    PlaybackState.STATE_PLAYING -> "tocando (PLAYING)"
    PlaybackState.STATE_PAUSED -> "pausado (PAUSED)"
    PlaybackState.STATE_STOPPED -> "parado (STOPPED)"
    PlaybackState.STATE_BUFFERING -> "carregando (BUFFERING)"
    PlaybackState.STATE_CONNECTING -> "conectando (CONNECTING)"
    PlaybackState.STATE_ERROR -> "erro (ERROR)"
    PlaybackState.STATE_NONE -> "nenhum (NONE)"
    else -> "outro ($state)"
}
