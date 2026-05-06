package eu.kanade.tachiyomi.ui.library.novel

import cafe.adriel.voyager.core.model.ScreenModel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelLibrarySettingsScreenModel(
    val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ScreenModel {

    fun setDisplayMode(mode: LibraryDisplayMode) {
        libraryPreferences.displayMode().set(mode)
    }
}
