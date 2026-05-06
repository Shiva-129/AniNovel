package eu.kanade.novel.ui.browse

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.api.MainAPI
import eu.kanade.novel.api.NovelAPIRepository
import eu.kanade.novel.api.SearchResponse
import eu.kanade.novel.providers.NovelProviderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val providers: List<NovelAPIRepository> = emptyList(),
    val selectedProvider: NovelAPIRepository? = null,
    val mainPage: List<SearchResponse> = emptyList(),
    val searchResults: List<SearchResponse> = emptyList(),
    val toolbarQuery: String = "",
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
)

class NovelBrowseScreenModel(
    private val fixedSourceName: String? = null,
) : ScreenModel {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state

    val currentSource: MainAPI?
        get() = if (fixedSourceName != null) {
            NovelProviderManager.getByName(fixedSourceName)
        } else {
            _state.value.selectedProvider?.api
        }

    init {
        val repos = if (fixedSourceName != null) {
            listOfNotNull(NovelProviderManager.getByName(fixedSourceName)?.let { NovelAPIRepository(it) })
        } else {
            NovelProviderManager.providers.map { NovelAPIRepository(it) }
        }
        _state.value = _state.value.copy(providers = repos, selectedProvider = repos.firstOrNull())
        repos.firstOrNull()?.let { loadMainPage(it) }
    }

    fun selectProvider(repo: NovelAPIRepository) {
        _state.value = _state.value.copy(selectedProvider = repo, mainPage = emptyList(), page = 1, error = null)
        loadMainPage(repo)
    }

    fun loadMainPage(repo: NovelAPIRepository? = _state.value.selectedProvider) {
        if (repo == null) return
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.loadMainPage(_state.value.page, null, null, null)
                .onSuccess { resp ->
                    _state.value = _state.value.copy(
                        mainPage = _state.value.mainPage + resp.list,
                        isLoading = false,
                        page = _state.value.page + 1,
                    )
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun setToolbarQuery(query: String?) {
        _state.value = _state.value.copy(toolbarQuery = query ?: "")
    }

    fun search(query: String) {
        val repo = _state.value.selectedProvider ?: return
        _state.value = _state.value.copy(query = query, isLoading = true, searchResults = emptyList(), error = null)
        screenModelScope.launch {
            repo.search(query)
                .onSuccess { _state.value = _state.value.copy(searchResults = it, isLoading = false) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(query = "", searchResults = emptyList())
    }
}
