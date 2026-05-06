package tachiyomi.data.repository.novel

import tachiyomi.data.novel.model.HistoryEntry
import tachiyomi.data.novel.model.Novel
import tachiyomi.data.novel.model.NovelChapter
import tachiyomi.data.novel.model.NovelDownloadState
import tachiyomi.data.novel.model.toReleaseStatus
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler

class NovelRepository(private val handler: NovelDatabaseHandler) {

    // ─── Novels ──────────────────────────────────────────────────────────────

    fun getFavoriteNovels(): Flow<List<Novel>> =
        handler.subscribeToList { novelsQueries.getFavoriteNovels(::mapNovel) }

    suspend fun getNovelById(id: Long): Novel =
        handler.awaitOne { novelsQueries.getNovelById(id, ::mapNovel) }

    suspend fun getNovelByUrlAndSource(url: String, source: String): Novel? =
        handler.awaitOneOrNull { novelsQueries.getNovelByUrlAndSource(url, source, ::mapNovel) }

    suspend fun insertNovel(
        source: String,
        url: String,
        title: String,
        author: String?,
        description: String?,
        genre: List<String>?,
        status: Int,
        thumbnailUrl: String?,
        favorite: Boolean,
        initialized: Boolean,
        dateAdded: Long,
        coverLastModified: Long,
    ): Long {
        handler.await {
            novelsQueries.insert(
                source = source,
                url = url,
                title = title,
                author = author,
                description = description,
                genre = genre,
                status = status.toLong(),
                thumbnailUrl = thumbnailUrl,
                favorite = favorite,
                initialized = initialized,
                dateAdded = dateAdded,
                coverLastModified = coverLastModified,
            )
        }
        return handler.awaitOneExecutable { novelsQueries.selectLastInsertedRowId() }
    }

    suspend fun updateFavorite(novelId: Long, favorite: Boolean) =
        handler.await { novelsQueries.updateFavorite(favorite = favorite, novelId = novelId) }

    suspend fun deleteNovelById(novelId: Long) =
        handler.await { novelsQueries.deleteNovelById(novelId) }

    // ─── Chapters ────────────────────────────────────────────────────────────

    fun getChaptersByNovelId(novelId: Long): Flow<List<NovelChapter>> =
        handler.subscribeToList { novel_chaptersQueries.getChaptersByNovelId(novelId, ::mapChapter) }

    suspend fun getFirstUnreadChapter(novelId: Long): NovelChapter? =
        handler.awaitOneOrNull { novel_chaptersQueries.getFirstUnreadChapter(novelId, ::mapChapter) }

    suspend fun insertChapter(
        novelId: Long,
        url: String,
        name: String,
        sourceOrder: Int,
        dateFetch: Long,
        dateUpload: Long,
    ): Long {
        handler.await {
            novel_chaptersQueries.insert(
                novelId = novelId,
                url = url,
                name = name,
                lastCharRead = 0,
                progress = 0,
                read = false,
                bookmark = false,
                sourceOrder = sourceOrder.toLong(),
                dateFetch = dateFetch,
                dateUpload = dateUpload,
                downloadStatus = NovelDownloadState.NOT_DOWNLOADED,
            )
        }
        return handler.awaitOneExecutable { novel_chaptersQueries.selectLastInsertedRowId() }
    }

    suspend fun updateReadProgress(chapterId: Long, read: Boolean, lastCharRead: Long, progress: Int) =
        handler.await {
            novel_chaptersQueries.updateReadProgress(
                read = read,
                lastCharRead = lastCharRead,
                progress = progress.toLong(),
                chapterId = chapterId,
            )
        }

    fun getRecentlyRead(limit: Long = 50): Flow<List<HistoryEntry>> =
        handler.subscribeToList {
            novel_chaptersQueries.getRecentlyReadChapters(limit) { _, novelId, url, name, _, _, _, _, _, _, _, _, lastModifiedAt, novelTitle, thumbnailUrl ->
                HistoryEntry(
                    novelTitle = novelTitle,
                    chapterName = name,
                    novelUrl = url,
                    apiName = "", // source stored in novels.source; join not exposed here — resolved at detail screen
                    lastReadAt = lastModifiedAt,
                )
            }
        }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private fun mapNovel(
        id: Long,
        source: String,
        url: String,
        title: String,
        author: String?,
        description: String?,
        genre: List<String>?,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        initialized: Boolean,
        dateAdded: Long,
        coverLastModified: Long,
        lastModifiedAt: Long,
    ) = Novel(
        id = id,
        source = source,
        url = url,
        title = title,
        author = author,
        description = description,
        genre = genre,
        status = status.toInt().toReleaseStatus(),
        thumbnailUrl = thumbnailUrl,
        favorite = favorite,
        initialized = initialized,
        dateAdded = dateAdded,
        coverLastModified = coverLastModified,
        lastModifiedAt = lastModifiedAt,
    )

    private fun mapChapter(
        id: Long,
        novelId: Long,
        url: String,
        name: String,
        lastCharRead: Long,
        progress: Long,
        read: Boolean,
        bookmark: Boolean,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        downloadStatus: NovelDownloadState,
        lastModifiedAt: Long,
    ) = NovelChapter(
        id = id,
        novelId = novelId,
        url = url,
        name = name,
        lastCharRead = lastCharRead,
        progress = progress.toInt(),
        read = read,
        bookmark = bookmark,
        sourceOrder = sourceOrder.toInt(),
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        downloadStatus = downloadStatus,
        lastModifiedAt = lastModifiedAt,
    )
}
