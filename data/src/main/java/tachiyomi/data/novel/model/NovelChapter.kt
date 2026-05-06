package tachiyomi.data.novel.model

data class NovelChapter(
    val id: Long,
    val novelId: Long,
    val url: String,
    val name: String,
    val lastCharRead: Long,
    val progress: Int,
    val read: Boolean,
    val bookmark: Boolean,
    val sourceOrder: Int,
    val dateFetch: Long,
    val dateUpload: Long,
    val downloadStatus: NovelDownloadState,
    val lastModifiedAt: Long,
) {
    val isDownloaded: Boolean
        get() = downloadStatus == NovelDownloadState.DOWNLOADED
}
