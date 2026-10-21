package app.novadrive.launcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.novadrive.launcher.AppContainer
import app.novadrive.launcher.apps.AppEntry
import app.novadrive.launcher.apps.Resolution
import app.novadrive.launcher.apps.ResolvedShortcut
import app.novadrive.launcher.apps.SearchText
import app.novadrive.launcher.settings.CityLocation
import app.novadrive.launcher.settings.Settings
import app.novadrive.launcher.settings.ShortcutSlot
import app.novadrive.launcher.weather.OpenMeteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Apps : Screen
    data object Settings : Screen
    data object NotificationAccess : Screen
    data object HeadunitHelp : Screen
    data object MediaDiagnostics : Screen
    data class Picker(val target: PickTarget) : Screen
}

sealed interface PickTarget {
    data class Shortcut(val slot: ShortcutSlot) : PickTarget
    data object Headunit : PickTarget
}

sealed interface CitySearch {
    data object Idle : CitySearch
    data object Loading : CitySearch
    data class Results(val items: List<CityLocation>) : CitySearch
    data class Failed(val message: String) : CitySearch
}

class LauncherViewModel(val c: AppContainer) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    val settings: StateFlow<Settings?> = c.settings.settings
    val media = c.media.state
    val weather = c.weather.state
    val clock = c.clock
    val apps = c.apps.apps

    val shortcuts: StateFlow<List<ResolvedShortcut>> = combine(apps, settings) { list, s ->
        val all = list.orEmpty()
        ShortcutSlot.entries.map { Resolution.shortcut(it, s?.shortcuts?.get(it), all) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val headunit: StateFlow<AppEntry?> = combine(apps, settings) { list, s ->
        Resolution.headunit(s?.headunitComponent, list.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val filteredApps: StateFlow<List<AppEntry>> = combine(apps, _query) { list, q ->
        val all = list.orEmpty()
        if (q.isBlank()) all else all.filter { SearchText.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _citySearch = MutableStateFlow<CitySearch>(CitySearch.Idle)
    val citySearch: StateFlow<CitySearch> = _citySearch.asStateFlow()

    // ---- Navegação ----

    fun open(screen: Screen) {
        if (screen !is Screen.Picker && screen != Screen.Apps) _query.value = ""
        _screen.value = screen
    }

    /** Voltar: Picker → Configurações; demais → HOME. Na HOME, não faz nada (é o launcher). */
    fun back() {
        _screen.value = when (val s = _screen.value) {
            is Screen.Picker -> if (s.target is PickTarget.Headunit && _fromHeadunitHelp) Screen.HeadunitHelp else Screen.Settings
            Screen.HeadunitHelp -> if (_fromSettings) Screen.Settings else Screen.Home
            Screen.MediaDiagnostics -> Screen.Settings
            else -> Screen.Home
        }
        _query.value = ""
    }

    private var _fromSettings = false
    private var _fromHeadunitHelp = false

    fun openHeadunitHelp(fromSettings: Boolean) {
        _fromSettings = fromSettings
        open(Screen.HeadunitHelp)
    }

    fun openPicker(target: PickTarget, fromHeadunitHelp: Boolean = false) {
        _fromHeadunitHelp = fromHeadunitHelp
        _query.value = ""
        _screen.value = Screen.Picker(target)
    }

    fun goHome() {
        _query.value = ""
        _screen.value = Screen.Home
    }

    fun setQuery(q: String) {
        _query.value = q.take(40)
    }

    // ---- Escolhas ----

    fun pick(target: PickTarget, app: AppEntry?) {
        when (target) {
            is PickTarget.Shortcut -> c.settings.setShortcut(target.slot, app?.key)
            PickTarget.Headunit -> c.settings.setHeadunit(app?.key)
        }
        back()
    }

    fun searchCity(q: String) {
        if (q.isBlank()) return
        _citySearch.value = CitySearch.Loading
        viewModelScope.launch {
            _citySearch.value = try {
                CitySearch.Results(withContext(Dispatchers.IO) { OpenMeteo.searchCity(q) })
            } catch (e: Exception) {
                CitySearch.Failed("Não foi possível buscar (sem internet?)")
            }
        }
    }

    fun chooseCity(city: CityLocation) {
        c.settings.setCity(city)
        _citySearch.value = CitySearch.Idle
    }

    fun onResume() {
        c.media.recheck()
        // Voltou da tela do sistema com o acesso concedido: segue para a HOME.
        if (_screen.value == Screen.NotificationAccess && c.media.hasAccess()) goHome()
    }
}
