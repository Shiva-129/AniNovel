package eu.kanade.novel.ui.library

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.data.model.Novel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tachiyomi.data.repository.novel.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelLibraryScreenModel(
    repository: NovelRepository = Injekt.get(),
) : ScreenModel {

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery: StateFlow<String?> = _searchQuery

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden

    private val allNovels = repository
        .getFavoriteNovels()
        .catch { emit(emptyList()) }

    val novels: StateFlow<List<Novel>> = combine(allNovels, _searchQuery, _showHidden) { novels, query, showHidden ->
        novels
            .filter { novel -> showHidden || novel.genre?.none { it.startsWith(".") } != false }
            .let { filtered ->
                if (query.isNullOrBlank()) filtered
                else filtered.filter { it.title.contains(query, ignoreCase = true) }
            }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(query: String?) {
        _searchQuery.value = query
    }

    fun toggleHidden() {
        _showHidden.value = !_showHidden.value
    }

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }
}
