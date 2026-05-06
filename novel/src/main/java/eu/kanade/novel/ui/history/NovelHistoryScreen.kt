package eu.kanade.novel.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.novel.ui.detail.NovelDetailScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import tachiyomi.data.novel.model.HistoryEntry
import tachiyomi.data.repository.novel.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelHistoryScreenModel(
    repository: NovelRepository = Injekt.get(),
) : ScreenModel {

    val history: StateFlow<List<HistoryEntry>> = repository
        .getRecentlyRead(limit = 50)
        .catch { emit(emptyList()) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

object NovelHistoryScreen : Screen {

    @Composable
    override fun Content() {
        val model = rememberScreenModel { NovelHistoryScreenModel() }
        val history by model.history.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold { padding ->
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No reading history", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    items(history, key = { "${it.novelUrl}${it.chapterName}" }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.novelTitle) },
                            supportingContent = { Text(entry.chapterName) },
                            modifier = Modifier.clickable {
                                navigator.push(NovelDetailScreen(entry.novelUrl, entry.apiName))
                            },
                        )
                    }
                }
            }
        }
    }
}
