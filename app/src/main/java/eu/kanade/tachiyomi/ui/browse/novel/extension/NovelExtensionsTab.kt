package eu.kanade.tachiyomi.ui.browse.novel.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.novel.ui.browse.NovelExtensionsScreen
import eu.kanade.novel.ui.browse.NovelExtensionsScreenModel
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.novelExtensionsTab(
    extensionsScreenModel: NovelExtensionsScreenModel,
): TabContent {
    val state by extensionsScreenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel_extensions,
        searchEnabled = true,
        content = { contentPadding, _ ->
            NovelExtensionsScreen(
                state = state,
                contentPadding = contentPadding,
                onToggleExtension = extensionsScreenModel::toggleExtension,
            )
        },
    )
}
