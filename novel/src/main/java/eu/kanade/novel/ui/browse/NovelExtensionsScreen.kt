package eu.kanade.novel.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun NovelExtensionsScreen(
    state: NovelExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    onToggleExtension: (NovelExtension) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = "No novel extensions found",
        )
        else -> FastScrollLazyColumn(
            contentPadding = contentPadding + topSmallPaddingValues,
        ) {
            state.items.forEach { (header, items) ->
                item(
                    contentType = "header",
                    key = "novelExtHeader-${header.hashCode()}",
                ) {
                    NovelExtensionHeader(
                        text = (header as NovelExtensionUiModel.Header.Text).text,
                        modifier = Modifier.animateItem(),
                    )
                }
                items(
                    items = items,
                    contentType = { "item" },
                    key = { "novelExt-${it.extension.name}" },
                ) { item ->
                    NovelExtensionItem(
                        modifier = Modifier.animateItem(),
                        item = item,
                        onToggle = { onToggleExtension(item.extension) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelExtensionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 8.dp).weight(1f),
            style = MaterialTheme.typography.header,
        )
    }
}

@Composable
private fun NovelExtensionItem(
    item: NovelExtensionUiModel.Item,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Book,
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(end = MaterialTheme.padding.medium),
            tint = if (item.isEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.extension.name, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.secondaryItemAlpha(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    Text(item.extension.lang.uppercase())
                    Text(item.extension.mainUrl)
                    if (!item.isEnabled) {
                        Text(
                            text = "DISABLED",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (item.isEnabled) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff,
                contentDescription = if (item.isEnabled) "Disable" else "Enable",
                tint = if (item.isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
