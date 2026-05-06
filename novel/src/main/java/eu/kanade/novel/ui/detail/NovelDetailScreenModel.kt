package eu.kanade.novel.ui.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.api.ChapterData
import eu.kanade.novel.api.LoadResponse
import eu.kanade.novel.api.NovelAPIRepository
import eu.kanade.novel.api.StreamResponse
import eu.kanade.novel.providers.NovelProviderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.data.repository.novel.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class DetailState(
    val response: LoadResponse? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val inLibrary: Boolean = false,
    val novelId: Long? = null,
    val readChapterUrls: Set<String> = emptySet(),
)

class NovelDetailScreenModel(
    private val url: String,
    private val apiName: String,
    private val repository: NovelRepository = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private val repo: NovelAPIRepository? =
        NovelProviderManager.getByName(apiName)?.let { NovelAPIRepository(it) }

    init { load() }

    fun load() {
        val r = repo ?: run {
            _state.value = DetailState(isLoading = false, error = "Provider '$apiName' not found")
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            r.load(url)
                .onSuccess { resp ->
                    val existing = repository.getNovelByUrlAndSource(url, apiName)
                    _state.value = _state.value.copy(
                        response = resp,
                        isLoading = false,
                        inLibrary = existing?.favorite == true,
                        novelId = existing?.id,
                    )
                    // Subscribe to chapter read status from DB
                    existing?.id?.let { novelId ->
                        repository.getChaptersByNovelId(novelId)
                            .onEach { chapters ->
                                _state.value = _state.value.copy(
                                    readChapterUrls = chapters.filter { it.read }.map { it.url }.toSet(),
                                )
                            }
                            .launchIn(screenModelScope)
                    }
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun toggleLibrary() {
        val resp = _state.value.response ?: return
        val nowInLibrary = !_state.value.inLibrary
        screenModelScope.launch {
            val id = _state.value.novelId ?: repository.insertNovel(
                source = apiName,
                url = url,
                title = resp.name,
                author = resp.author,
                description = resp.synopsis,
                genre = resp.tags,
                status = resp.status?.ordinal ?: 0,
                thumbnailUrl = resp.posterUrl,
                favorite = nowInLibrary,
                initialized = true,
                dateAdded = System.currentTimeMillis(),
                coverLastModified = System.currentTimeMillis(),
            )
            if (_state.value.novelId != null) {
                repository.updateFavorite(id, nowInLibrary)
            }
            _state.value = _state.value.copy(inLibrary = nowInLibrary, novelId = id)

            if (nowInLibrary) {
                chapters().forEachIndexed { index, chapter ->
                    repository.insertChapter(
                        novelId = id,
                        url = chapter.url,
                        name = chapter.name,
                        sourceOrder = index,
                        dateFetch = System.currentTimeMillis(),
                        dateUpload = 0L,
                    )
                }
                // Subscribe to read status after inserting chapters
                repository.getChaptersByNovelId(id)
                    .onEach { chapters ->
                        _state.value = _state.value.copy(
                            readChapterUrls = chapters.filter { it.read }.map { it.url }.toSet(),
                        )
                    }
                    .launchIn(screenModelScope)
            }
        }
    }

    fun chapters(): List<ChapterData> =
        (_state.value.response as? StreamResponse)?.data ?: emptyList()

    suspend fun getFirstUnreadChapterIndex(): Int? {
        val novelId = _state.value.novelId ?: return null
        val firstUnread = repository.getFirstUnreadChapter(novelId) ?: return null
        return chapters().indexOfFirst { it.url == firstUnread.url }.takeIf { it >= 0 }
    }

    suspend fun toggleChapterRead(chapterUrl: String, read: Boolean) {
        val novelId = _state.value.novelId ?: return
        val dbChapter = repository.getChaptersByNovelId(novelId)
            .first()
            .firstOrNull { ch -> ch.url == chapterUrl } ?: return
        repository.updateReadProgress(dbChapter.id, read = read, lastCharRead = 0, progress = if (read) 100 else 0)
    }
}
