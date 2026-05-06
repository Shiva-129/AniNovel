package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.novel.ui.browse.NovelSourceOptionsDialog
import eu.kanade.novel.ui.browse.NovelSourceUiModel
import eu.kanade.novel.ui.browse.NovelSourcesScreen
import eu.kanade.novel.ui.browse.NovelSourcesScreenModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.novelSourcesTab(
    sourcesScreenModel: NovelSourcesScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val state by sourcesScreenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel_sources,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalNovelSearchScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            NovelSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source -> navigator.push(BrowseNovelSourceScreen(source.name)) },
                onClickPin = sourcesScreenModel::togglePin,
                onLongClickItem = sourcesScreenModel::showDialog,
            )

            val dialog = state.dialog
            if (dialog != null) {
                val source = dialog.source
                val isPinned = state.items
                    .filterIsInstance<NovelSourceUiModel.Item>()
                    .any { it.source.name == source.name && it.isPinned }
                NovelSourceOptionsDialog(
                    source = source,
                    isPinned = isPinned,
                    onClickPin = { sourcesScreenModel.togglePin(source); sourcesScreenModel.closeDialog() },
                    onClickDisable = { sourcesScreenModel.toggleSource(source); sourcesScreenModel.closeDialog() },
                    onDismiss = sourcesScreenModel::closeDialog,
                )
            }
        },
    )
}
