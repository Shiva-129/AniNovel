package eu.kanade.novel.download

import android.content.Context
import android.util.Log
import eu.kanade.novel.api.ChapterData
import eu.kanade.novel.api.EpubResponse
import eu.kanade.novel.api.ErrorLoadingException
import eu.kanade.novel.api.LoadResponse
import eu.kanade.novel.api.StreamResponse
import eu.kanade.novel.api.NovelAPIRepository
import eu.kanade.novel.data.DOWNLOAD_EPUB_SIZE
import eu.kanade.novel.data.DOWNLOAD_FOLDER
import eu.kanade.novel.data.DOWNLOAD_OFFSET
import eu.kanade.novel.data.DOWNLOAD_TOTAL
import eu.kanade.novel.data.NovelDataStore.getKey
import eu.kanade.novel.data.NovelDataStore.removeKey
import eu.kanade.novel.data.NovelDataStore.setKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "NovelDownloader"
const val LOCAL_EPUB = "local_epub.epub"
const val LOCAL_EPUB_MIN_SIZE = 1000L

data class DownloadProgressState(
    val state: NovelDownloadState,
    val progress: Long,
    val downloaded: Long,
    val total: Long,
    val etaMs: Long?,
)

data class DownloadData(
    val url: String,
    val name: String,
    val author: String?,
    val posterUrl: String?,
    val synopsis: String?,
    val apiName: String,
    val addedAt: Long = System.currentTimeMillis(),
)

object NovelDownloader {

    private val _progressMap = MutableStateFlow<Map<Int, DownloadProgressState>>(emptyMap())
    val progressMap: StateFlow<Map<Int, DownloadProgressState>> = _progressMap

    private val mutex = Mutex()
    private val activeIds = mutableSetOf<Int>()
    private val pendingActions = mutableMapOf<Int, DownloadActionType>()

    enum class DownloadActionType { Pause, Resume, Stop }

    fun sanitize(name: String): String =
        name.replace(Regex("[|\\\\?*<\":>+\\[\\]/']+"), " ").replace(Regex("\\s+"), " ").trim()

    fun getDirectory(apiName: String, author: String, name: String): String {
        val sep = File.separatorChar
        return "$sep$apiName$sep$author$sep$name".replace("$sep$sep", "$sep")
    }

    fun getFilename(apiName: String, author: String, name: String, index: Int): String =
        "${getDirectory(apiName, author, name)}${File.separatorChar}$index.txt"

    fun getImageFilename(apiName: String, author: String, name: String): String =
        "${getDirectory(apiName, author, name)}${File.separatorChar}poster.jpg"

    fun generateId(apiName: String, author: String?, name: String): Int =
        "${sanitize(apiName)}${sanitize(author ?: "")}${sanitize(name)}".hashCode()

    fun generateId(load: LoadResponse, apiName: String): Int =
        generateId(apiName, load.author, load.name)

    fun addAction(id: Int, action: DownloadActionType) {
        pendingActions[id] = action
    }

    private fun consumeAction(id: Int): DownloadActionType? =
        pendingActions.remove(id)

    private fun updateProgress(id: Int, state: DownloadProgressState) {
        _progressMap.value = _progressMap.value + (id to state)
    }

    suspend fun downloadChapters(
        context: Context,
        load: StreamResponse,
        api: NovelAPIRepository,
    ) = withContext(Dispatchers.IO) {
        val id = generateId(load, api.name)
        mutex.withLock {
            if (activeIds.contains(id)) return@withContext
            activeIds.add(id)
        }

        val sApi = sanitize(api.name)
        val sAuthor = sanitize(load.author ?: "")
        val sName = sanitize(load.name)
        val filesDir = context.filesDir
        val start = context.getKey<Int>(DOWNLOAD_OFFSET, id.toString()) ?: 0
        val range = start until load.data.size

        context.setKey(DOWNLOAD_TOTAL, id.toString(), load.data.size)
        context.setKey(DOWNLOAD_FOLDER, id.toString(), DownloadData(load.url, load.name, load.author, load.posterUrl, load.synopsis, api.name))

        var currentState = NovelDownloadState.DOWNLOADING
        var timePerLoad = 1000.0

        try {
            for (index in range) {
                val chapter = load.data.getOrNull(index) ?: continue

                // handle pause/stop
                while (true) {
                    when (consumeAction(id)) {
                        DownloadActionType.Pause -> currentState = NovelDownloadState.PAUSED
                        DownloadActionType.Resume -> currentState = NovelDownloadState.DOWNLOADING
                        DownloadActionType.Stop -> { currentState = NovelDownloadState.NOT_DOWNLOADED; break }
                        null -> {}
                    }
                    if (currentState != NovelDownloadState.PAUSED) break
                    delay(200)
                }
                if (currentState == NovelDownloadState.NOT_DOWNLOADED) break

                val filepath = filesDir.toString() + getFilename(sApi, sAuthor, sName, index)
                val file = File(filepath)
                if (file.exists() && file.length() > 10) continue

                val before = System.currentTimeMillis()
                val ok = downloadChapter(filepath, api, chapter)
                if (!ok) { currentState = NovelDownloadState.ERROR }

                timePerLoad = (System.currentTimeMillis() - before) * 0.05 + timePerLoad * 0.95
                updateProgress(id, DownloadProgressState(
                    state = currentState,
                    progress = index.toLong() + 1,
                    downloaded = index.toLong() + 1 - start,
                    total = load.data.size.toLong(),
                    etaMs = (timePerLoad * (range.last - index)).toLong(),
                ))
                if (currentState == NovelDownloadState.ERROR) break
            }
            if (currentState == NovelDownloadState.DOWNLOADING) {
                updateProgress(id, DownloadProgressState(NovelDownloadState.DOWNLOADED, load.data.size.toLong(), (range.last - start + 1).toLong(), load.data.size.toLong(), 0))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Download failed", t)
            updateProgress(id, DownloadProgressState(NovelDownloadState.ERROR, 0, 0, load.data.size.toLong(), null))
        } finally {
            mutex.withLock { activeIds.remove(id) }
        }
    }

    private suspend fun downloadChapter(
        filepath: String,
        api: NovelAPIRepository,
        chapter: ChapterData,
        maxTries: Int = 5,
    ): Boolean = withContext(Dispatchers.IO) {
        val file = File(filepath)
        if (file.exists() && file.length() > 0) return@withContext true
        file.parentFile?.mkdirs()
        if (file.isDirectory) file.delete()
        repeat(maxTries) {
            val html = api.loadHtml(chapter.url)
            if (!html.isNullOrBlank()) {
                file.createNewFile()
                file.writeText("${chapter.name}\n$html")
                return@withContext true
            }
            delay(5000)
        }
        false
    }

    fun buildEpub(
        context: Context,
        author: String?,
        name: String,
        apiName: String,
        synopsis: String?,
    ) {
        val sApi = sanitize(apiName)
        val sAuthor = sanitize(author ?: "")
        val sName = sanitize(name)
        val filesDir = context.filesDir
        val id = "$sApi$sAuthor$sName".hashCode()
        val dir = File(filesDir.toString() + getDirectory(sApi, sAuthor, sName))
        val epubFile = File(dir, LOCAL_EPUB)

        if (epubFile.exists() && epubFile.length() > LOCAL_EPUB_MIN_SIZE) return

        // EPUB building not available — epublib dependency not bundled
        Log.w(TAG, "buildEpub: epublib not available, skipping")
    }

    fun deleteNovel(context: Context, author: String?, name: String, apiName: String) {
        val sApi = sanitize(apiName)
        val sAuthor = sanitize(author ?: "")
        val sName = sanitize(name)
        val id = generateId(apiName, author, name)
        File(context.filesDir.toString() + getDirectory(sApi, sAuthor, sName)).deleteRecursively()
        context.removeKey(DOWNLOAD_TOTAL, id.toString())
        context.removeKey(DOWNLOAD_EPUB_SIZE, id.toString())
        context.removeKey(DOWNLOAD_OFFSET, id.toString())
        context.removeKey(DOWNLOAD_FOLDER, id.toString())
        _progressMap.value = _progressMap.value - id
    }

    fun downloadInfo(context: Context, author: String?, name: String, apiName: String): Pair<Long, Long>? {
        val sApi = sanitize(apiName)
        val sAuthor = sanitize(author ?: "")
        val sName = sanitize(name)
        val id = generateId(apiName, author, name)
        val dir = File(context.filesDir.toString() + getDirectory(sApi, sAuthor, sName))
        val epub = File(dir, LOCAL_EPUB)
        if (epub.exists() && epub.length() > LOCAL_EPUB_MIN_SIZE) return 1L to 1L
        val files = dir.listFiles()?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }?.sorted()
        if (files.isNullOrEmpty()) return null
        val start = context.getKey<Int>(DOWNLOAD_OFFSET, id.toString()) ?: 0
        val startIdx = maxOf(files.indexOfFirst { it >= start }, 0)
        var last = files[startIdx]
        var count = startIdx + 1
        for (i in startIdx + 1 until files.size) {
            if (files[i] == last + 1) { count++; last = files[i] } else break
        }
        val total = context.getKey<Int>(DOWNLOAD_TOTAL, id.toString()) ?: return null
        return (last + 1).toLong() to total.toLong()
    }
}
