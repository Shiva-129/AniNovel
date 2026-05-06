package eu.kanade.novel.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.novel.download.DownloadProgressState
import eu.kanade.novel.download.NovelDownloadState
import eu.kanade.novel.download.NovelDownloader

object NovelDownloadsScreen : Screen {

    @Composable
    override fun Content() {
        val progressMap by NovelDownloader.progressMap.collectAsState()

        Scaffold { padding ->
            if (progressMap.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No active downloads", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    items(progressMap.entries.toList(), key = { it.key }) { (id, state) ->
                        DownloadItem(id, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(id: Int, state: DownloadProgressState) {
    ListItem(
        headlineContent = { Text("Novel #$id") },
        supportingContent = {
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.progress.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        trailingContent = {
            Text(
                when (state.state) {
                    NovelDownloadState.DOWNLOADING -> "${state.progress}/${state.total}"
                    NovelDownloadState.DOWNLOADED -> "Done"
                    NovelDownloadState.PAUSED -> "Paused"
                    NovelDownloadState.ERROR -> "Error"
                    else -> state.state.name
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
