package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.novel.ui.browse.NovelBrowseScreenModel
import eu.kanade.novel.ui.browse.NovelGridCard
import eu.kanade.novel.ui.detail.NovelDetailScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.presentation.components.SearchToolbar
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

data class BrowseNovelSourceScreen(val sourceName: String) : Screen {

    @Composable
    override fun Content() {
        val model = rememberScreenModel { NovelBrowseScreenModel(fixedSourceName = sourceName) }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        var displayMode by remember { mutableStateOf<LibraryDisplayMode>(LibraryDisplayMode.CompactGrid) }
        var selectingDisplayMode by remember { mutableStateOf(false) }

        val source = model.currentSource

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    navigateUp = { navigator.pop() },
                    titleContent = { AppBarTitle(sourceName) },
                    searchQuery = state.toolbarQuery.ifEmpty { null },
                    onChangeSearchQuery = { model.setToolbarQuery(it) },
                    onSearch = { model.search(it) },
                    onClickCloseSearch = {
                        model.setToolbarQuery(null)
                        model.clearSearch()
                    },
                    actions = {
                        AppBarActions(
                            actions = persistentListOf<AppBar.AppBarAction>().builder()
                                .apply {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_display_mode),
                                            icon = if (displayMode == LibraryDisplayMode.List) {
                                                Icons.AutoMirrored.Filled.ViewList
                                            } else {
                                                Icons.Filled.ViewModule
                                            },
                                            onClick = { selectingDisplayMode = true },
                                        ),
                                    )
                                    if (source != null) {
                                        add(
                                            AppBar.OverflowAction(
                                                title = stringResource(MR.strings.action_open_in_web_view),
                                                onClick = { uriHandler.openUri(source.mainUrl) },
                                            ),
                                        )
                                    }
                                }
                                .build(),
                        )
                        DropdownMenu(
                            expanded = selectingDisplayMode,
                            onDismissRequest = { selectingDisplayMode = false },
                        ) {
                            RadioMenuItem(
                                text = { Text(stringResource(MR.strings.action_display_comfortable_grid)) },
                                isChecked = displayMode == LibraryDisplayMode.ComfortableGrid,
                            ) {
                                selectingDisplayMode = false
                                displayMode = LibraryDisplayMode.ComfortableGrid
                            }
                            RadioMenuItem(
                                text = { Text(stringResource(MR.strings.action_display_grid)) },
                                isChecked = displayMode == LibraryDisplayMode.CompactGrid,
                            ) {
                                selectingDisplayMode = false
                                displayMode = LibraryDisplayMode.CompactGrid
                            }
                            RadioMenuItem(
                                text = { Text(stringResource(MR.strings.action_display_list)) },
                                isChecked = displayMode == LibraryDisplayMode.List,
                            ) {
                                selectingDisplayMode = false
                                displayMode = LibraryDisplayMode.List
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when {
                    state.isLoading && state.mainPage.isEmpty() && state.searchResults.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    state.error != null && state.mainPage.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        }
                    else -> {
                        val displayList = if (state.query.isNotEmpty()) state.searchResults else state.mainPage
                        val columns = when (displayMode) {
                            LibraryDisplayMode.List -> GridCells.Fixed(1)
                            LibraryDisplayMode.ComfortableGrid -> GridCells.Adaptive(160.dp)
                            else -> GridCells.Adaptive(120.dp)
                        }
                        LazyVerticalGrid(
                            columns = columns,
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(displayList) { item ->
                                NovelGridCard(item) {
                                    navigator.push(NovelDetailScreen(item.url, item.apiName))
                                }
                            }
                            item {
                                if (state.query.isEmpty()) {
                                    if (!state.isLoading) {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                            Text(
                                                stringResource(MR.strings.novel_load_more),
                                                modifier = Modifier.clickable { model.loadMainPage() },
                                            )
                                        }
                                    } else {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
