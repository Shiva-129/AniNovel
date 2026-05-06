package eu.kanade.novel.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.novel.api.StreamResponse
import eu.kanade.novel.ui.reader.NovelReaderScreen
import kotlinx.coroutines.launch
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox

data class NovelDetailScreen(val url: String, val apiName: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { NovelDetailScreenModel(url, apiName) }
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val isReading = state.readChapterUrls.isNotEmpty()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.response?.name ?: "Novel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            floatingActionButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Start / Resume FAB
                    ExtendedFloatingActionButton(
                        text = { Text(if (isReading) "Resume" else "Start") },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            scope.launch {
                                val idx = model.getFirstUnreadChapterIndex()
                                    ?: if (model.chapters().isNotEmpty()) 0 else null
                                if (idx != null) {
                                    navigator.push(NovelReaderScreen(url, apiName, idx))
                                }
                            }
                        },
                    )
                    // Favorite FAB
                    FloatingActionButton(onClick = { model.toggleLibrary() }) {
                        Icon(
                            if (state.inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (state.inLibrary) "Remove from library" else "Add to library",
                        )
                    }
                }
            },
        ) { padding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val resp = state.response!!
                    LazyColumn(contentPadding = padding) {
                        item {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AsyncImage(
                                    model = resp.posterUrl,
                                    contentDescription = resp.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(100.dp).height(150.dp),
                                )
                                Column {
                                    Text(resp.name, style = MaterialTheme.typography.titleLarge)
                                    resp.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    resp.status?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        item {
                            resp.synopsis?.let {
                                Text(it, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        item {
                            HorizontalDivider()
                            Text(
                                "${model.chapters().size} chapters",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(model.chapters()) { chapter ->
                            val isRead = state.readChapterUrls.contains(chapter.url)
                            val toggleReadAction = SwipeAction(
                                icon = {
                                    Icon(
                                        imageVector = if (isRead) Icons.Outlined.RemoveDone else Icons.Outlined.Done,
                                        contentDescription = null,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                },
                                background = MaterialTheme.colorScheme.primaryContainer,
                                onSwipe = {
                                    scope.launch { model.toggleChapterRead(chapter.url, !isRead) }
                                },
                            )
                            SwipeableActionsBox(
                                modifier = Modifier.clipToBounds(),
                                startActions = listOf(toggleReadAction),
                                endActions = listOf(toggleReadAction),
                                swipeThreshold = 40.dp,
                                backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
                            ) {
                                Text(
                                    chapter.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val idx = model.chapters().indexOf(chapter)
                                            navigator.push(NovelReaderScreen(url, apiName, idx))
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalContentColor.current.copy(alpha = if (isRead) 0.38f else 1f),
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
