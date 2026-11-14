package app.potatotv.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.potatotv.ServiceLocator
import app.potatotv.data.model.AppSettings
import app.potatotv.data.model.MediaRow
import app.potatotv.data.model.MetaPreview
import app.potatotv.data.sites.SiteDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val rows: List<MediaRow> = emptyList(),
    val hero: MetaPreview? = null,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    var state by mutableStateOf(HomeState())
        private set

    private var started = false

    init {
        load()
    }

    fun load(force: Boolean = false) {
        if (started && !force) return
        started = true
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val settings = runCatching { ServiceLocator.prefs.settings.first() }
                .getOrDefault(AppSettings())

            // In FMHY-sources mode, refresh the user's site list from the directory
            // before we render. Metadata itself still comes from Cinemeta, which is
            // the only free, key-less catalogue PotatoTV ships with.
            if (settings.catalogProvider == "fmhy-sources") {
                refreshSources(settings.siteDirectoryUrl)
            }

            runCatching { ServiceLocator.cinemeta.homeRows() }
                .onSuccess { rows ->
                    state = state.copy(
                        loading = false,
                        rows = rows,
                        hero = rows.firstOrNull()?.items?.firstOrNull(),
                        error = null
                    )
                }
                .onFailure {
                    state = state.copy(
                        loading = false,
                        error = it.message ?: "Could not reach the metadata service."
                    )
                }
        }
    }

    private suspend fun refreshSources(directoryUrl: String) {
        if (directoryUrl.isBlank()) return
        runCatching {
            val parsed = SiteDirectory.parse(
                app.potatotv.data.net.Http.getText(directoryUrl)
            )
            parsed.sites.forEach { ServiceLocator.prefs.addSite(it) }
            parsed.titleSites.forEach { ServiceLocator.prefs.addSite(it) }
            parsed.deeplinks
                .mapNotNull { SiteDirectory.deeplinkToSite(it) }
                .forEach { ServiceLocator.prefs.addSite(it) }
        }
    }
}
