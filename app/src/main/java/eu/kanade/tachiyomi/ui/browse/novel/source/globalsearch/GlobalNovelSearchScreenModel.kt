package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.api.NovelAPIRepository
import eu.kanade.novel.api.SearchResponse
import eu.kanade.novel.providers.NovelProviderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlobalNovelSearchState(
    val searchQuery: String = "",
    val results: Map<String, List<SearchResponse>> = emptyMap(),
    val loading: Set<String> = emptySet(),
)

class GlobalNovelSearchScreenModel(
    initialQuery: String = "",
) : ScreenModel {

    private val _state = MutableStateFlow(GlobalNovelSearchState(searchQuery = initialQuery))
    val state: StateFlow<GlobalNovelSearchState> = _state

    private var searchJob: Job? = null

    init {
        if (initialQuery.isNotEmpty()) search(initialQuery)
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun search(query: String = _state.value.searchQuery) {
        if (query.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = query, results = emptyMap(), loading = emptySet()) }

        searchJob = screenModelScope.launch {
            NovelProviderManager.providers.forEach { api ->
                launch {
                    _state.update { it.copy(loading = it.loading + api.name) }
                    NovelAPIRepository(api).search(query)
                        .onSuccess { results ->
                            _state.update { state ->
                                state.copy(
                                    results = state.results + (api.name to results),
                                    loading = state.loading - api.name,
                                )
                            }
                        }
                        .onFailure {
                            _state.update { state ->
                                state.copy(loading = state.loading - api.name)
                            }
                        }
                }
            }
        }
    }
}
