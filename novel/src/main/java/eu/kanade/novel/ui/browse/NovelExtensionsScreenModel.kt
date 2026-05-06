package eu.kanade.novel.ui.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.data.NovelPreferences
import eu.kanade.novel.providers.NovelProviderManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionsScreenModel(
    private val preferences: NovelPreferences = Injekt.get(),
) : StateScreenModel<NovelExtensionsScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO { collectExtensions() }
        preferences.enabledSources().changes()
            .onEach { collectExtensions() }
            .launchIn(screenModelScope)
    }

    private fun collectExtensions(query: String? = mutableState.value.searchQuery) {
        val disabled = preferences.enabledSources().get()
        val searchQuery = query?.lowercase() ?: ""

        val byLang = NovelProviderManager.providers
            .filter { searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) }
            .groupBy { it.lang }
            .toSortedMap()

        val items = mutableMapOf<NovelExtensionUiModel.Header, List<NovelExtensionUiModel.Item>>()
        byLang.forEach { (lang, providers) ->
            val header = NovelExtensionUiModel.Header.Text(lang.uppercase())
            items[header] = providers.map { api ->
                val isEnabled = api.name !in disabled
                val ext = if (isEnabled) NovelExtension.Installed(api) else NovelExtension.Disabled(api)
                NovelExtensionUiModel.Item(ext, isEnabled)
            }
        }

        mutableState.update { it.copy(isLoading = false, items = items) }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
        collectExtensions(query)
    }

    fun toggleExtension(extension: NovelExtension) {
        val disabled = preferences.enabledSources().get().toMutableSet()
        val name = extension.name
        if (name in disabled) disabled.remove(name) else disabled.add(name)
        preferences.enabledSources().set(disabled)
        collectExtensions()
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: Map<NovelExtensionUiModel.Header, List<NovelExtensionUiModel.Item>> = emptyMap(),
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }
}
