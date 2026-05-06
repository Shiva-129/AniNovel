package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.novel.ui.detail.NovelDetailScreen
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen

data class GlobalNovelSearchScreen(val initialQuery: String = "") : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { GlobalNovelSearchScreenModel(initialQuery) }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    navigateUp = { navigator.pop() },
                    titleContent = { AppBarTitle("Novel Search") },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = { screenModel.updateSearchQuery(it ?: "") },
                    onSearch = { screenModel.search(it) },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            when {
                state.results.isEmpty() && state.loading.isEmpty() ->
                    EmptyScreen(
                        message = "Search for novels across all sources",
                        modifier = Modifier.padding(padding),
                    )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
                ) {
                    state.loading.forEach { sourceName ->
                        if (sourceName !in state.results) {
                            item(key = "loading-$sourceName") {
                                SourceRow(
                                    sourceName = sourceName,
                                    isLoading = true,
                                    results = emptyList(),
                                    onClickItem = {},
                                )
                            }
                        }
                    }
                    state.results.forEach { (sourceName, results) ->
                        if (results.isNotEmpty()) {
                            item(key = "source-$sourceName") {
                                SourceRow(
                                    sourceName = sourceName,
                                    isLoading = sourceName in state.loading,
                                    results = results,
                                    onClickItem = { url ->
                                        navigator.push(NovelDetailScreen(url, sourceName))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    sourceName: String,
    isLoading: Boolean,
    results: List<eu.kanade.novel.api.SearchResponse>,
    onClickItem: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = sourceName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (isLoading && results.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { item ->
                    Column(
                        modifier = Modifier
                            .width(100.dp)
                            .clickable { onClickItem(item.url) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(100.dp).height(140.dp),
                        )
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
