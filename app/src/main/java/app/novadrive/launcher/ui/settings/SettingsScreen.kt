package app.novadrive.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.novadrive.launcher.BuildConfig
import app.novadrive.launcher.R
import app.novadrive.launcher.media.MediaState
import app.novadrive.launcher.settings.FontSize
import app.novadrive.launcher.settings.LocationMode
import app.novadrive.launcher.settings.Settings
import app.novadrive.launcher.settings.ThemeMode
import app.novadrive.launcher.settings.WallpaperChoice
import app.novadrive.launcher.system.Diagnostics
import app.novadrive.launcher.system.HomeWatcher
import app.novadrive.launcher.ui.CitySearch
import app.novadrive.launcher.ui.LauncherActions
import app.novadrive.launcher.ui.LauncherViewModel
import app.novadrive.launcher.ui.PickTarget
import app.novadrive.launcher.ui.PillButton
import app.novadrive.launcher.ui.Screen
import app.novadrive.launcher.ui.ScreenHeader
import app.novadrive.launcher.ui.TouchMin
import app.novadrive.launcher.ui.tap
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: LauncherViewModel, actions: LauncherActions, s: Settings) {
    val repo = vm.c.settings
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val headunit by vm.headunit.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val homes = remember { vm.c.apps.otherHomeApps() }
    val brand = vm.c.wallpaper.brandingPresent

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Configurações", vm::back)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            section("Aparência") {
                Options("Tema", ThemeMode.entries, s.theme, { it.label }, repo::setTheme)
                Hint("Automático: escuro do pôr ao nascer do sol (pelo clima) ou das 18h às 6h.")
                Options(
                    "Papel de parede",
                    if (brand) WallpaperChoice.entries else listOf(WallpaperChoice.REAR, WallpaperChoice.NONE),
                    s.wallpaper,
                    { if (!brand && it == WallpaperChoice.REAR) "Padrão" else it.label },
                    repo::setWallpaper,
                )
                Options("Tamanho da fonte", FontSize.entries, s.fontSize, { it.label }, repo::setFontSize)
                Toggle(
                    "Modo performance",
                    "Desliga animações, barra de progresso e usa capas menores. Recomendado se a central travar.",
                    s.performanceMode,
                    repo::setPerformanceMode,
                )
            }
            section("Atalhos da tela inicial") {
                shortcuts.forEach { sc ->
                    Row_(
                        title = sc.slot.label,
                        value = sc.app?.let { it.label + if (sc.automatic) " (automático)" else "" } ?: "Não encontrado — toque para escolher",
                        onClick = { vm.openPicker(PickTarget.Shortcut(sc.slot)) },
                    )
                }
            }
            section("Android Auto") {
                Row_(
                    "App do botão Android Auto",
                    headunit?.let { "${it.label} · ${it.packageName}" } ?: "Open Headunit não instalado",
                    onClick = { vm.openPicker(PickTarget.Headunit) },
                )
                Row_("Como instalar e configurar", "Open Headunit no lugar do ZLink", onClick = { vm.openHeadunitHelp(fromSettings = true) })
            }
            section("Tela inicial do fabricante") {
                val watcherOn by HomeWatcher.connected.collectAsStateWithLifecycle()
                val ctx = LocalContext.current
                val inSystem = remember(watcherOn) { HomeWatcher.isEnabledInSystem(ctx) }
                Toggle(
                    "Substituir tela inicial do fabricante",
                    "Se o botão HOME abrir o launcher da central, o NovaDrive volta para a frente. Também abre o NovaDrive ao ligar a central.",
                    s.replaceOemHome,
                    repo::setReplaceOemHome,
                )
                Row_(
                    "Serviço de acessibilidade",
                    when {
                        watcherOn -> "Ativo"
                        inSystem -> "Ligado no Android, aguardando o serviço iniciar"
                        else -> "Desligado — toque e ative \"NovaDrive — vigia da tela inicial\""
                    },
                    onClick = actions::openAccessibilitySettings,
                )
                Info(
                    "Launcher do fabricante detectado",
                    homes.joinToString { it.packageName }.ifEmpty { "Nenhum (só o NovaDrive responde ao HOME)" },
                )
            }
            section("Clima") {
                Toggle("Mostrar clima", "Open-Meteo, sem conta. Envia só a posição aproximada (~1 km).", s.weatherEnabled, repo::setWeatherEnabled)
                if (s.weatherEnabled) {
                    Options("Localização", LocationMode.entries, s.locationMode, { it.label }, repo::setLocationMode)
                    when (s.locationMode) {
                        LocationMode.GPS -> if (!vm.c.location.hasPermission()) {
                            Row_("Permitir localização", "Necessário para o clima pelo GPS", onClick = actions::requestLocationPermission)
                        }
                        LocationMode.CITY -> CityPicker(vm, s)
                    }
                }
            }
            section("Permissões") {
                Row_(
                    "Acesso às notificações",
                    if (media == MediaState.NoPermission) "Não concedido — o cartão de música não funciona" else "Concedido",
                    onClick = { vm.open(Screen.NotificationAccess) },
                )
            }
            section("Sistema e caminho de volta") {
                Row_("Escolher a tela inicial padrão", "Use para voltar ao launcher original da central", onClick = actions::openHomeSettings)
                homes.forEach { h ->
                    Row_("Abrir ${h.label}", "Launcher instalado · ${h.packageName}", onClick = { actions.openOtherHome(h) })
                }
                Row_("Configurações do Android", "Wi-Fi, Bluetooth, data e hora", onClick = actions::openSystemSettings)
            }
            section("Diagnóstico") {
                Row_("Diagnóstico de mídia", "Sessões de mídia ativas e o que cada app está enviando", onClick = { vm.open(Screen.MediaDiagnostics) })
                DiagnosticsBlock()
            }
        }
    }
}

private fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item(key = title) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Row_(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TouchMin)
            .clip(RoundedCornerShape(14.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Só leitura (sem toque). */
@Composable
private fun Info(title: String, value: String) {
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = TouchMin).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TouchMin)
            .clip(RoundedCornerShape(14.dp))
            .tap { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Opções em botões grandes (FilterChip padrão tem 32 dp: pequeno demais para o carro). */
@Composable
private fun <T> Options(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { o ->
                val on = o == selected
                Row(
                    Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (on) cs.primary else cs.onSurface.copy(alpha = 0.08f))
                        .tap { onSelect(o) }
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface)
                }
            }
        }
    }
}

@Composable
private fun CityPicker(vm: LauncherViewModel, s: Settings) {
    val search by vm.citySearch.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text("Cidade atual: ${s.city?.name ?: "nenhuma"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Ex.: Campinas") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.searchCity(text) }),
            )
            Spacer(Modifier.width(12.dp))
            PillButton("Buscar", { focus.clearFocus(); vm.searchCity(text) }, icon = R.drawable.ic_search)
        }
        when (val r = search) {
            CitySearch.Idle -> Unit
            CitySearch.Loading -> Hint("Buscando…")
            is CitySearch.Failed -> Hint(r.message)
            is CitySearch.Results -> if (r.items.isEmpty()) Hint("Nenhuma cidade encontrada") else r.items.forEach { c ->
                Row_(c.name, "%.2f, %.2f".format(java.util.Locale.US, c.latitude, c.longitude), onClick = { vm.chooseCity(c) })
            }
        }
    }
}

@Composable
private fun DiagnosticsBlock() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val mem = remember(tick) { Diagnostics.systemMemory(ctx) }
    val pss = remember(tick) { Diagnostics.pssMb() }
    val dm = LocalResources.current.displayMetrics
    Column(Modifier.padding(horizontal = 8.dp)) {
        Line("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Line("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · ${Build.MODEL}")
        Line("Tela", "${dm.widthPixels}×${dm.heightPixels} px · ${dm.densityDpi} dpi")
        Line("Abertura a frio", Diagnostics.coldStartMs?.let { "$it ms (meta < 1500)" } ?: "—")
        Line("RAM do NovaDrive (PSS)", "$pss MB (meta < 120)")
        Line("RAM livre do sistema", "${mem.availMb} de ${mem.totalMb} MB${if (mem.low) " · BAIXA" else ""}")
        Line("Limite por app", "${mem.appLimitMb} MB")
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(220.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}
