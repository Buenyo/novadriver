package app.novadrive.launcher.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.novadrive.launcher.R
import app.novadrive.launcher.apps.AppEntry
import app.novadrive.launcher.apps.Resolution
import app.novadrive.launcher.ui.GlassCard
import app.novadrive.launcher.ui.PillButton
import app.novadrive.launcher.ui.ScreenHeader

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text("$n.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Code(text: String) {
    SelectionContainer {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
fun NotificationAccessScreen(packageName: String, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Acesso às notificações", onBack)
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Icon(painterResource(R.drawable.ic_notifications), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Por que o NovaDrive pede isso?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "O Android só deixa um launcher ver e controlar a música de outros apps (Spotify, YouTube Music, " +
                        "rádio, Bluetooth) se ele tiver acesso às notificações.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "O NovaDrive usa esse acesso só para o cartão \"Tocando agora\". Ele não lê, não guarda e não envia " +
                        "o conteúdo das suas notificações.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                PillButton("Abrir configurações", onOpenSettings, icon = R.drawable.ic_settings, primary = true)
            }
            GlassCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Como liberar", style = MaterialTheme.typography.titleMedium)
                    Step(1, "Toque em \"Abrir configurações\".")
                    Step(2, "Ative \"NovaDrive — controles de mídia\" e confirme.")
                    Step(3, "Volte para a tela inicial. O cartão aparece sozinho.")
                    Spacer(Modifier.height(12.dp))
                    Text("Se a central não abrir essa tela", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Alguns firmwares escondem o menu. Pelo computador, com ADB conectado, rode na pasta do projeto:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Code("scripts/grant-notifications.sh")
                    Text(
                        "O script adiciona o NovaDrive e mantém os outros apps que já tinham acesso ($packageName).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun HeadunitHelpScreen(
    detected: AppEntry?,
    onBack: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onPickManually: () -> Unit,
    onOpenReleases: () -> Unit,
    onUnknownSources: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Android Auto", onBack)
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Icon(painterResource(R.drawable.ic_car), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                if (detected != null) {
                    Text("Pronto: ${detected.label}", style = MaterialTheme.typography.titleLarge)
                    Text(detected.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    PillButton("Abrir agora", { onOpen(detected) }, icon = R.drawable.ic_play_circle, primary = true)
                } else {
                    Text("Open Headunit não encontrado", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O botão Android Auto abre o Open Headunit, app livre (AGPL-3.0) que recebe o Android Auto do " +
                            "celular pelo USB. Ele substitui o ZLink.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PillButton("Escolher app manualmente", onPickManually)
            }
            GlassCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Como instalar", style = MaterialTheme.typography.titleMedium)
                    Step(1, "Baixe o APK em github.com/andreknieriem/open-headunit/releases (versão sem \"_debug\").")
                    Step(2, "Copie para um pendrive ou use ADB: adb install -r <arquivo>.apk")
                    Step(3, "Se instalar pelo pendrive, libere \"Fontes desconhecidas\".")
                    Step(4, "Abra o Open Headunit uma vez e rode o assistente de otimização.")
                    Step(5, "Para o USB abrir o Open Headunit e não o ZLink, veja docs/open-headunit.md.")
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Página de downloads", onOpenReleases, icon = R.drawable.ic_link)
                        PillButton("Liberar fontes desconhecidas", onUnknownSources)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pacote esperado: ${Resolution.OPEN_HEADUNIT_PACKAGE} (o NovaDrive também reconhece variantes com \"headunit\" no nome).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
