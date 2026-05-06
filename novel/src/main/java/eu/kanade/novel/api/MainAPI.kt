package eu.kanade.novel.api

import androidx.annotation.WorkerThread
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.sync.Mutex
import org.jsoup.Jsoup

const val NOVEL_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

/**
 * Base class for all novel provider implementations.
 * Migrated from QuickNovel's MainAPI.kt with package and dependency updates.
 */
abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"
    open val iconId: Int = 0
    open val iconBackgroundId: Int = 0

    /** ISO 639-1 language code */
    open val lang = "en"

    open val usesCloudFlareKiller = false

    /** Shared HTTP client injected by NovelProviderManager */
    lateinit var app: Requests

    fun fixPosterHeaders(headers: Map<String, String>?): Map<String, String>? {
        return if (usesCloudFlareKiller) {
            (headers ?: emptyMap()) + mapOf("User-Agent" to NOVEL_USER_AGENT)
        } else {
            headers
        }
    }

    open val rateLimitTime: Long = 0
    val hasRateLimit: Boolean get() = rateLimitTime > 0L
    val rateLimitMutex: Mutex = Mutex()

    open val hasMainPage = false

    open val mainCategories: List<Pair<String, String>> = listOf()
    open val orderBys: List<Pair<String, String>> = listOf()
    open val tags: List<Pair<String, String>> = listOf()

    open suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        throw NotImplementedError()
    }

    open val hasReviews: Boolean = false
    open suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean = false,
    ): List<UserReview> {
        throw NotImplementedError()
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun loadHtml(url: String): String? {
        throw NotImplementedError()
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith('/')) return mainUrl + url
    return "$mainUrl/$url"
}

fun makeLinkSafe(url: String): String = url.replace("http://", "https://")

val String?.textClean: String?
    get() = this
        ?.replace("\\.([A-z]|\\+)".toRegex(), "$1")
        ?.replace("\\+([A-z])".toRegex(), "$1")

/**
 * Strips HTML and cleans scraped novel chapter content.
 */
fun stripHtml(
    txt: String,
    chapterName: String? = null,
    chapterIndex: Int? = null,
    stripAuthorNotes: Boolean = false,
): String {
    val document = Jsoup.parse(txt)
    try {
        if (stripAuthorNotes) {
            document.select("div.qnauthornotecontainer").remove()
        }
        if (chapterName != null && chapterIndex != null) {
            for (a in document.allElements) {
                if (a != null && a.hasText() &&
                    (a.text() == chapterName || (a.tagName() == "h3" &&
                        a.text().startsWith("Chapter ${chapterIndex + 1}")))
                ) {
                    a.remove()
                    break
                }
            }
        }
    } catch (_: Exception) { }

    return document.html()
        .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "")
        .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "")
}

// ─── Data classes ────────────────────────────────────────────────────────────

data class HeadMainPageResponse(
    val url: String,
    val list: List<SearchResponse>,
)

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
)

data class UserReview(
    val review: String,
    val reviewTitle: String?,
    val username: String?,
    val reviewDate: String?,
    val avatarUrl: String?,
    val rating: Int?,
    val ratings: List<Pair<Int, String>>?,
)

data class SearchResponse(
    val name: String,
    val url: String,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var latestChapter: String? = null,
    val apiName: String,
    var posterHeaders: Map<String, String>? = null,
)

fun MainAPI.newSearchResponse(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: SearchResponse.() -> Unit = {},
): SearchResponse {
    val builder = SearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)
    return builder
}

typealias ReleaseStatus = tachiyomi.data.novel.model.ReleaseStatus

fun LoadResponse.setStatus(status: String?): Boolean {
    if (status == null) return false
    this.status = when (status.lowercase().trim()) {
        "ongoing", "on-going", "on_going", "releasing" -> ReleaseStatus.Ongoing
        "completed", "complete", "done" -> ReleaseStatus.Completed
        "hiatus", "paused", "pause" -> ReleaseStatus.Paused
        "dropped", "drop" -> ReleaseStatus.Dropped
        "stub", "stubbed" -> ReleaseStatus.Stubbed
        else -> return false
    }
    return true
}

interface LoadResponse {
    val url: String
    val name: String
    var author: String?
    var posterUrl: String?
    var rating: Int?
    var peopleVoted: Int?
    var views: Int?
    var synopsis: String?
    var tags: List<String>?
    var status: ReleaseStatus?
    var posterHeaders: Map<String, String>?
    val apiName: String
    var related: List<SearchResponse>?
}

data class StreamResponse(
    override val url: String,
    override val name: String,
    val data: List<ChapterData>,
    override val apiName: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var nextChapter: ChapterData? = null,
    override var related: List<SearchResponse>? = null,
) : LoadResponse

suspend fun MainAPI.newStreamResponse(
    name: String,
    url: String,
    data: List<ChapterData>,
    fix: Boolean = true,
    initializer: suspend StreamResponse.() -> Unit = {},
): StreamResponse {
    val builder = StreamResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        data = data,
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)
    return builder
}

data class DownloadLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
    val kbPerSec: Long = 1,
) : DownloadLinkType

data class DownloadExtractLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
) : DownloadLinkType

interface DownloadLinkType {
    val url: String
    val name: String
}

@WorkerThread
suspend fun DownloadExtractLink.get(app: Requests): NiceResponse =
    app.get(makeLinkSafe(url), headers, referer, params, cookies)

@WorkerThread
suspend fun DownloadLink.get(app: Requests): NiceResponse =
    app.get(makeLinkSafe(url), headers, referer, params, cookies)

data class EpubResponse(
    override val url: String,
    override val name: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var downloadLinks: List<DownloadLink>,
    var downloadExtractLinks: List<DownloadExtractLink>,
    override val apiName: String,
    override var related: List<SearchResponse>? = null,
) : LoadResponse

suspend fun MainAPI.newEpubResponse(
    name: String,
    url: String,
    links: List<DownloadLinkType>,
    fix: Boolean = true,
    initializer: suspend EpubResponse.() -> Unit = {},
): EpubResponse {
    val builder = EpubResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        downloadLinks = links.filterIsInstance<DownloadLink>(),
        downloadExtractLinks = links.filterIsInstance<DownloadExtractLink>(),
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)
    return builder
}

data class ChapterData(
    val name: String,
    val url: String,
    var dateOfRelease: String? = null,
    val views: Int? = null,
)

fun MainAPI.newChapterData(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: ChapterData.() -> Unit = {},
): ChapterData {
    val builder = ChapterData(name = name, url = if (fix) fixUrl(url) else url)
    builder.initializer()
    return builder
}

// ─── Missing QuickNovel utility stubs ────────────────────────────────────────

const val USER_AGENT = NOVEL_USER_AGENT

val DefaultImagesHeaders = mapOf("User-Agent" to NOVEL_USER_AGENT)

fun logError(t: Throwable) {
    android.util.Log.e("NovelProvider", t.message ?: "Unknown error", t)
}

inline fun <reified T> parseJson(json: String): T =
    com.fasterxml.jackson.databind.ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .readValue(json, T::class.java)

fun String?.clean(): String? = this?.trim()?.ifBlank { null }

fun String?.synopsis(): String = this?.trim() ?: ""

fun String?.toRate(): Int? = this?.trim()?.toFloatOrNull()?.times(200)?.toInt()

fun String.toUrlBuilderSafe(): UrlBuilder = UrlBuilder(this)

class UrlBuilder(private var url: String) {
    fun addPath(vararg parts: String): UrlBuilder {
        url = url.trimEnd('/') + "/" + parts.joinToString("/")
        return this
    }
    fun ifCase(condition: Boolean, block: UrlBuilder.() -> UrlBuilder): UrlBuilder =
        if (condition) block() else this
    override fun toString(): String = url
}

inline fun safe(block: () -> Unit) {
    try { block() } catch (_: Throwable) {}
}
