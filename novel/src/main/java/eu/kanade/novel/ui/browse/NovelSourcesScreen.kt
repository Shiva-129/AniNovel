package eu.kanade.novel.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.novel.api.MainAPI
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus

@Composable
fun NovelSourcesScreen(
    state: NovelSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (MainAPI) -> Unit,
    onClickPin: (MainAPI) -> Unit,
    onLongClickItem: (MainAPI) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = "No novel sources available",
        )
        else -> LazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
            state.items.forEach { model ->
                when (model) {
                    is NovelSourceUiModel.Header -> item(key = "header-${model.language}") {
                        NovelSourceHeader(language = model.language)
                    }
                    is NovelSourceUiModel.Item -> item(key = "source-${model.source.name}") {
                        NovelSourceItem(
                            source = model.source,
                            isPinned = model.isPinned,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelSourceHeader(language: String, modifier: Modifier = Modifier) {
    Text(
        text = language.uppercase(),
        modifier = modifier.padding(
            horizontal = MaterialTheme.padding.medium,
            vertical = MaterialTheme.padding.small,
        ),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun NovelSourceItem(
    source: MainAPI,
    isPinned: Boolean,
    onClickItem: (MainAPI) -> Unit,
    onLongClickItem: (MainAPI) -> Unit,
    onClickPin: (MainAPI) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickItem(source) }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Book,
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(end = MaterialTheme.padding.medium),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                source.mainUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            )
        }
        IconButton(onClick = { onClickPin(source) }) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned) "Unpin" else "Pin",
                tint = if (isPinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
            )
        }
    }
}

@Composable
fun NovelSourceOptionsDialog(
    source: MainAPI,
    isPinned: Boolean,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(source.name) },
        text = {
            Column {
                Text(
                    text = if (isPinned) "Unpin" else "Pin",
                    modifier = Modifier.clickable(onClick = onClickPin).fillMaxWidth().padding(vertical = 16.dp),
                )
                Text(
                    text = "Disable",
                    modifier = Modifier.clickable(onClick = onClickDisable).fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}
