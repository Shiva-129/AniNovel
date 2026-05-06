package eu.kanade.novel.ui.browse

import eu.kanade.novel.api.MainAPI

/**
 * A novel "extension" is a built-in provider (MainAPI subclass).
 * Enabled/disabled state is persisted via NovelPreferences.
 */
sealed interface NovelExtension {
    val name: String
    val lang: String
    val mainUrl: String

    data class Installed(val api: MainAPI) : NovelExtension {
        override val name get() = api.name
        override val lang get() = api.lang
        override val mainUrl get() = api.mainUrl
    }

    data class Disabled(val api: MainAPI) : NovelExtension {
        override val name get() = api.name
        override val lang get() = api.lang
        override val mainUrl get() = api.mainUrl
    }
}

object NovelExtensionUiModel {
    sealed interface Header {
        data class Text(val text: String) : Header
    }

    data class Item(
        val extension: NovelExtension,
        val isEnabled: Boolean,
    )
}
