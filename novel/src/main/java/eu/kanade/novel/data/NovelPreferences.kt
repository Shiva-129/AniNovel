package eu.kanade.novel.data

import tachiyomi.core.common.preference.PreferenceStore

class NovelPreferences(private val preferenceStore: PreferenceStore) {

    fun enabledSources() = preferenceStore.getStringSet("novel_enabled_sources", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet("novel_pinned_sources", emptySet())
}
