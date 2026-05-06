package eu.kanade.novel.providers

import android.net.Uri
import eu.kanade.novel.api.ErrorLoadingException
import eu.kanade.novel.api.HeadMainPageResponse
import eu.kanade.novel.api.LoadResponse
import eu.kanade.novel.api.MainAPI
import eu.kanade.novel.api.*

import eu.kanade.novel.api.SearchResponse
import eu.kanade.novel.api.fixUrlNull
import eu.kanade.novel.api.newChapterData
import eu.kanade.novel.api.newSearchResponse
import eu.kanade.novel.api.newStreamResponse
import eu.kanade.novel.api.setStatus

abstract class WPReader : MainAPI() {
    override val name = ""
    override val mainUrl = ""
    override val lang = "id"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "China" to "china",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Jepang" to "jepang",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mystery" to "mystery",
        "Original (Inggris)" to "original-inggris",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Seinen Xuanhuan" to "seinen-xuanhuan",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
    )
    /*
    open override val orderBys: List<Pair<String, String>> = listOf(
        "Latest Update" to  "update",
        "Most Views" to  "popular",
        "Rating" to  "rating",
        "A-Z" to  "title",
        "Latest Add" to  "latest",
    )
    */
    // open val country: List<String> = listOf("jepang", "china", "korea", "unknown",)

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = mainUrl
            .toUrlBuilderSafe()
            .ifCase(tag != "") { addPath("genre", "$tag") }
            .ifCase(page > 1) { addPath("page", page.toString()) }
            .toString()

        val res = app.get(url).document
            .select(if (tag == "") ".flexbox3-content > a" else ".flexbox2-content > a")
            .mapNotNull { element ->
                newSearchResponse(
                    name = element.attr("title") ?: return@mapNotNull null,
                    url = element.attr("href")
                ) {
                    posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
                }
            }

        return HeadMainPageResponse(url, res)
    }

    override suspend fun loadHtml(url: String): String? {
        val con = app.get(url).document

        con.select("input, div.reader-settings, label.showsetting, div.entry-pagination, div.ads, div.comment").remove()

        val res = con.select("div.content > div.container > div > *")
            .joinToString("<br>")

        return res.ifBlank { null }
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${Uri.encode(query)}"
        return app.get(url).document
            .select("div.flexbox2 > div.flexbox2-item > div.flexbox2-content > a")
            .mapNotNull { element ->
                newSearchResponse(
                    name = element.attr("title") ?: return@mapNotNull null,
                    url = element.attr("href")
                ) {
                    posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val data = doc.select("div.flexch-infoz > a")
            .mapNotNull { dat ->
                newChapterData(name = dat.attr("title").clean() ?: "", url = dat.attr("href").clean() ?: "") {
                    dateOfRelease = dat.selectFirst("span.date")?.text()?.clean() ?: ""
                }
            }.reversed()

        return newStreamResponse(
            url = url,
            name = doc.selectFirst(".series-titlex > h2")?.text()?.clean()
                ?: throw ErrorLoadingException("No name"),
            data = data
        ) {
            author = doc.selectFirst("li:contains(Author)")
                ?.selectFirst("span")?.text()?.clean()
            posterUrl = doc.selectFirst("div.series-thumb img")
                ?.attr("src")
            rating = doc.selectFirst("span[itemprop=ratingValue]")?.text()?.toRate()

            synopsis = doc.selectFirst(".series-synops")?.text()?.synopsis() ?: ""
            tags = doc.selectFirst("div.series-genres")?.select("a")
                ?.mapNotNull { tag -> tag.text().clean() }
            setStatus(doc.selectFirst("span.status")?.text())
        }
    }
}
