package app.novadrive.launcher.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.novadrive.launcher.R
import app.novadrive.launcher.apps.AppEntry
import app.novadrive.launcher.ui.AppTile
import app.novadrive.launcher.ui.LauncherViewModel
import app.novadrive.launcher.ui.PillButton
import app.novadrive.launcher.ui.RoundIconButton

/**
 * Gaveta de apps. Também é usada como seletor ([pickTitle] != null) para atalhos e Android Auto.
 * A busca não abre o teclado sozinha: na central o teclado cobre metade da tela.
 */
@Composable
fun AppDrawerScreen(
    vm: LauncherViewModel,
    onBack: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onLongPress: ((AppEntry) -> Unit)?,
    pickTitle: String? = null,
    onAutomatic: (() -> Unit)? = null,
) {
    val apps by vm.filteredApps.collectAsStateWithLifecycle()
    val all by vm.apps.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ic_back, "Voltar", onBack)
            Spacer(Modifier.width(12.dp))
            if (pickTitle != null) {
                Text(pickTitle, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.padding(end = 12.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f).height(64.dp),
                singleLine = true,
                placeholder = { Text("Buscar app") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { RoundIconButton(R.drawable.ic_close, "Limpar busca", { vm.setQuery("") }, size = 48.dp) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(32.dp),
                colors = OutlinedTextFieldDefaults.colors(),
            )
            if (onAutomatic != null) {
                Spacer(Modifier.width(12.dp))
                PillButton("Automático", onAutomatic)
            }
        }
        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Carregando apps…") }
            apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum app encontrado") }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    AppTile(vm.c.apps, app, onClick = { focus.clearFocus(); onOpen(app) }, onLongClick = onLongPress?.let { { it(app) } })
                }
            }
        }
    }
}
