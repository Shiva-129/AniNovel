package eu.kanade.novel.ui.browse

import eu.kanade.novel.api.MainAPI

sealed interface NovelSourceUiModel {
    data class Item(val source: MainAPI, val isPinned: Boolean = false) : NovelSourceUiModel
    data class Header(val language: String) : NovelSourceUiModel
}
