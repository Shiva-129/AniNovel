package eu.kanade.novel.ui.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.api.MainAPI
import eu.kanade.novel.data.NovelPreferences
import eu.kanade.novel.providers.NovelProviderManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSourcesScreenModel(
    private val preferences: NovelPreferences = Injekt.get(),
) : StateScreenModel<NovelSourcesScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO { collectSources() }

        preferences.pinnedSources().changes()
            .onEach { collectSources() }
            .launchIn(screenModelScope)
    }

    private fun collectSources(query: String? = mutableState.value.searchQuery) {
        val pinned = preferences.pinnedSources().get()
        val enabled = preferences.enabledSources().get()

        val allProviders = NovelProviderManager.providers
            .filter { it.name !in enabled }
            .filter { query.isNullOrBlank() || it.name.contains(query, ignoreCase = true) }

        val pinnedItems = allProviders
            .filter { it.name in pinned }
            .map { NovelSourceUiModel.Item(it, isPinned = true) }

        val byLang = allProviders
            .filter { it.name !in pinned }
            .groupBy { it.lang }
            .toSortedMap()

        val items = mutableListOf<NovelSourceUiModel>()
        if (pinnedItems.isNotEmpty()) {
            items += NovelSourceUiModel.Header("📌")
            items += pinnedItems
        }
        byLang.forEach { (lang, sources) ->
            items += NovelSourceUiModel.Header(lang)
            items += sources.map { NovelSourceUiModel.Item(it, isPinned = false) }
        }

        mutableState.update { it.copy(isLoading = false, items = items) }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
        collectSources(query)
    }

    fun togglePin(source: MainAPI) {
        val pinned = preferences.pinnedSources().get().toMutableSet()
        if (source.name in pinned) pinned.remove(source.name) else pinned.add(source.name)
        preferences.pinnedSources().set(pinned)
        collectSources()
    }

    fun toggleSource(source: MainAPI) {
        val enabled = preferences.enabledSources().get().toMutableSet()
        if (source.name in enabled) enabled.remove(source.name) else enabled.add(source.name)
        preferences.enabledSources().set(enabled)
        collectSources()
    }

    fun showDialog(source: MainAPI) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    data class Dialog(val source: MainAPI)

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<NovelSourceUiModel> = emptyList(),
        val dialog: Dialog? = null,
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }
}
