package tachiyomi.data.novel.model

data class HistoryEntry(
    val novelTitle: String,
    val chapterName: String,
    val novelUrl: String,
    val apiName: String,
    val lastReadAt: Long,
)
