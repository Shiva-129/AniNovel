package eu.kanade.novel.ui.reader

import android.content.Context
import android.text.Spanned
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.novel.api.ChapterData
import eu.kanade.novel.api.NovelAPIRepository
import eu.kanade.novel.api.StreamResponse
import eu.kanade.novel.providers.NovelProviderManager
import eu.kanade.novel.tts.TTSHelper
import eu.kanade.novel.tts.TTSSession
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tachiyomi.data.repository.novel.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class ReaderState(
    val chapterIndex: Int = 0,
    val chapters: List<ChapterData> = emptyList(),
    val html: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val ttsStatus: TTSHelper.TTSStatus = TTSHelper.TTSStatus.IsStopped,
    val ttsLine: TTSHelper.TTSLine? = null,
    val fontSize: Int = 16,
    val scrollPosition: Int = 0,
)

class NovelReaderViewModel(
    private val novelUrl: String,
    private val apiName: String,
    initialChapterIndex: Int,
    private val repository: NovelRepository = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(ReaderState(chapterIndex = initialChapterIndex))
    val state: StateFlow<ReaderState> = _state

    private val repo: NovelAPIRepository? =
        NovelProviderManager.getByName(apiName)?.let { NovelAPIRepository(it) }

    private var ttsSession: TTSSession? = null

    init { loadNovelAndChapter(initialChapterIndex) }

    private fun loadNovelAndChapter(index: Int) {
        val r = repo ?: run {
            _state.value = _state.value.copy(isLoading = false, error = "Provider not found")
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            r.load(novelUrl)
                .onSuccess { resp ->
                    val chapters = (resp as? StreamResponse)?.data ?: emptyList()
                    _state.value = _state.value.copy(chapters = chapters)
                    loadChapter(index)
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadChapter(index: Int) {
        val r = repo ?: return
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        _state.value = _state.value.copy(isLoading = true, chapterIndex = index, html = null)
        screenModelScope.launch {
            val html = r.loadHtml(chapter.url)
            _state.value = _state.value.copy(html = html, isLoading = false)
            // Mark chapter as read in DB
            val novelEntry = repository.getNovelByUrlAndSource(novelUrl, apiName)
            if (novelEntry != null) {
                val dbChapters = repository.getChaptersByNovelId(novelEntry.id).first()
                val dbChapter = dbChapters.firstOrNull { ch -> ch.url == chapter.url }
                if (dbChapter != null) {
                    repository.updateReadProgress(dbChapter.id, read = true, lastCharRead = 0, progress = 100)
                }
            }
        }
    }

    fun nextChapter() {
        val next = _state.value.chapterIndex + 1
        if (next < _state.value.chapters.size) loadChapter(next)
    }

    fun prevChapter() {
        val prev = _state.value.chapterIndex - 1
        if (prev >= 0) loadChapter(prev)
    }

    fun skipForward() {
        ttsSession?.skipForward()
    }

    fun skipBackward() {
        ttsSession?.skipBackward()
    }

    fun setFontSize(size: Int) { _state.value = _state.value.copy(fontSize = size.coerceIn(10, 32)) }

    fun initTTS(context: Context) {
        if (ttsSession != null) return
        ttsSession = TTSSession(context) { action ->
            when (action) {
                TTSHelper.TTSActionType.Pause -> pauseTTS()
                TTSHelper.TTSActionType.Resume -> resumeTTS()
                TTSHelper.TTSActionType.Stop -> stopTTS()
                TTSHelper.TTSActionType.Next -> nextChapter()
            }
            true
        }
    }

    fun startTTS(context: Context, markwon: Markwon) {
        initTTS(context)
        val html = _state.value.html ?: return
        val processed = TTSHelper.preParseHtml(html, authorNotes = true)
        val spanned: Spanned = TTSHelper.render(processed, markwon)
        val lines = TTSHelper.ttsParseText(spanned.toString(), _state.value.chapterIndex)
        if (lines.isEmpty()) return
        _state.value = _state.value.copy(ttsStatus = TTSHelper.TTSStatus.IsRunning, ttsLine = lines.first())
        screenModelScope.launch {
            ttsSession?.register()
            var i = 0
            while (i in lines.indices) {
                val session = ttsSession ?: break
                // Handle skip forward/backward from volume keys
                val skip = session.pendingSkip
                if (skip != 0) {
                    session.pendingSkip = 0
                    session.interruptTTS()
                    i = (i + skip).coerceIn(0, lines.size - 1)
                }
                val line = lines[i]
                val next = lines.getOrNull(i + 1)
                _state.value = _state.value.copy(ttsLine = line)
                val id = session.speak(line, next) { _state.value.ttsStatus == TTSHelper.TTSStatus.IsStopped }
                session.waitForOr(id, { _state.value.ttsStatus == TTSHelper.TTSStatus.IsStopped || session.pendingSkip != 0 }) {}
                if (_state.value.ttsStatus == TTSHelper.TTSStatus.IsStopped) break
                if (session.pendingSkip == 0) i++
            }
            if (_state.value.ttsStatus != TTSHelper.TTSStatus.IsStopped) stopTTS()
        }
    }

    fun pauseTTS() {
        ttsSession?.interruptTTS()
        _state.value = _state.value.copy(ttsStatus = TTSHelper.TTSStatus.IsPaused)
    }

    fun resumeTTS() { _state.value = _state.value.copy(ttsStatus = TTSHelper.TTSStatus.IsRunning) }

    fun stopTTS() {
        ttsSession?.interruptTTS()
        _state.value = _state.value.copy(ttsStatus = TTSHelper.TTSStatus.IsStopped, ttsLine = null)
    }

    override fun onDispose() {
        ttsSession?.release()
        ttsSession = null
    }
}
