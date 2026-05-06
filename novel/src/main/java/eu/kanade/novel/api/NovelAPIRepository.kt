package eu.kanade.novel.api

import android.util.Log
import eu.kanade.novel.providers.NovelProviderManager
import org.jsoup.Jsoup

private const val TAG = "NovelAPIRepository"

data class OnGoingSearch(
    val apiName: String,
    val data: Result<List<SearchResponse>>,
)

/**
 * Repository wrapping a single novel provider (MainAPI).
 *
 * Migrated from QuickNovel's APIRepository with:
 *  - Removed dependency on QuickNovel-specific Resource/safeApiCall — now uses Kotlin's built-in Result.
 *  - HTTP client injected rather than accessed via static MainActivity.app.
 *  - iconId/iconBackgroundId removed (Compose UI will use provider name-based icons).
 */
class NovelAPIRepository(val api: MainAPI) {

    private val unixTime: Long
        get() = System.currentTimeMillis() / 1000L

    companion object {
        private val cache = mutableListOf<SavedLoadResponse>()
        private var cacheIndex = 0
        private const val CACHE_SIZE = 20
        private const val CACHE_TIME_SEC = 60 * 10 // 10 minutes
    }

    private data class SavedLoadResponse(
        val unixTime: Long,
        val response: LoadResponse,
        val hash: Pair<String, String>,
    )

    val name: String get() = api.name
    val mainUrl: String get() = api.mainUrl
    val hasReviews: Boolean get() = api.hasReviews
    val rateLimitTime: Long get() = api.rateLimitTime
    val hasMainPage: Boolean get() = api.hasMainPage
    val mainCategories: List<Pair<String, String>> get() = api.mainCategories
    val orderBys: List<Pair<String, String>> get() = api.orderBys
    val tags: List<Pair<String, String>> get() = api.tags

    suspend fun load(url: String, allowCache: Boolean = true): Result<LoadResponse> {
        return runCatching {
            try {
                if (api.hasRateLimit) api.rateLimitMutex.lock()

                val fixedUrl = api.fixUrl(url)
                val lookingForHash = api.name to fixedUrl

                if (allowCache) {
                    synchronized(cache) {
                        for (item in cache) {
                            if (item.hash == lookingForHash &&
                                (unixTime - item.unixTime) < CACHE_TIME_SEC
                            ) {
                                return@runCatching item.response
                            }
                        }
                    }
                }

                val response = api.load(fixedUrl) ?: error("No data from provider")

                if (allowCache) {
                    val saved = SavedLoadResponse(unixTime, response, lookingForHash)
                    synchronized(cache) {
                        if (cache.size > CACHE_SIZE) {
                            cache[cacheIndex] = saved
                            cacheIndex = (cacheIndex + 1) % CACHE_SIZE
                        } else {
                            cache.add(saved)
                        }
                    }
                }

                response
            } finally {
                if (api.hasRateLimit) api.rateLimitMutex.unlock()
            }
        }
    }

    suspend fun search(query: String): Result<List<SearchResponse>> = runCatching {
        api.search(query) ?: error("No data from provider")
    }

    suspend fun loadHtml(url: String): String? {
        return try {
            api.loadHtml(api.fixUrl(url))?.removeAds()
        } catch (e: Exception) {
            Log.e(TAG, "loadHtml failed", e)
            null
        }
    }

    suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean = false,
    ): Result<List<UserReview>> = runCatching {
        api.loadReviews(url, page, showSpoilers)
    }

    suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): Result<HeadMainPageResponse> = runCatching {
        api.loadMainPage(page, mainCategory, orderBy, tag)
    }
}

// ─── Ad stripping ────────────────────────────────────────────────────────────

private fun String?.removeAds(): String? {
    if (this.isNullOrBlank()) return null
    return try {
        val document = Jsoup.parse(this)
        document.select("small.ads-title").remove()
        document.select("script").remove()
        document.select("iframe").remove()
        document.select(".adsbygoogle").remove()
        document.html()
    } catch (t: Throwable) {
        Log.e(TAG, "removeAds failed", t)
        this
    }
}
