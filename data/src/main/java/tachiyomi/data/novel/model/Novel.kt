package tachiyomi.data.novel.model

data class Novel(
    val id: Long,
    val source: String,
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: ReleaseStatus?,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val initialized: Boolean,
    val dateAdded: Long,
    val coverLastModified: Long,
    val lastModifiedAt: Long,
) {
    val statusInt: Int
        get() = when (status) {
            ReleaseStatus.Ongoing -> 1
            ReleaseStatus.Completed -> 2
            ReleaseStatus.Paused -> 3
            ReleaseStatus.Dropped -> 4
            ReleaseStatus.Stubbed -> 5
            null -> 0
        }
}
