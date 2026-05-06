package eu.kanade.novel.providers

import android.net.Uri
import eu.kanade.novel.api.ChapterData
import eu.kanade.novel.api.ErrorLoadingException
import eu.kanade.novel.api.HeadMainPageResponse
import eu.kanade.novel.api.LoadResponse
import eu.kanade.novel.api.MainAPI

import eu.kanade.novel.api.SearchResponse
import eu.kanade.novel.api.newChapterData
import eu.kanade.novel.api.newSearchResponse
import eu.kanade.novel.api.newStreamResponse


class NovelManiaProvider : MainAPI() {

    override val name = "Novelmania"
    override val mainUrl = "https://novelmania.com.br"
    override val lang = "pt-pt"
    override val hasMainPage = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {

        val url = "$mainUrl/novels?page%5Bpage%5D=$page"
        val document = app.get(url).document

        val items = document.select("div.row div.top-novels").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst(".novel-title")?.text() ?: return@mapNotNull null

            newSearchResponse(title, href) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }
        }

        return HeadMainPageResponse(url, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".novel-info h1")?.text()  ?: throw ErrorLoadingException("Title not found")


        // AUTHOR
        var author: String? = null
        document.select(".novel-info span b").forEach { b ->
            if (b.text().contains("Autor")) {
                val parent = b.parent()
                b.remove()
                author = parent?.text()?.trim()
            }
        }

        val chapters = mutableListOf<ChapterData>()

        val volumes = document.select("#accordion .card-header button")

        volumes.forEach { volTag ->
            val target = volTag.attr("data-target") // ej: #volume1

            val chapterTags = document.select("$target li a[href]")

            chapterTags.forEachIndexed { index, a ->
                val name = a.selectFirst("strong")?.text()
                    ?: "Chapter ${index + 1}"

                val link = a.attr("href")

                chapters.add(newChapterData("${volTag.text()} $name", link))
            }
        }

        return newStreamResponse(title, url, chapters) {
            this.posterUrl = document.selectFirst(".novel-img img")?.attr("src")
            this.synopsis =  document.selectFirst("#info .text")?.text()
            this.author = author
            this.tags =  document.select("#info .tags a[href^=\"/genero/\"]")
                .mapNotNull { it.attr("title") }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document

        val content = document.selectFirst("#chapter-content")
            ?.children()
            ?.joinToString("</br>") { it.outerHtml() }

        return content
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/novels?titulo=${Uri.encode(query)}"
        val document = app.get(url).document

        return document.select("div.row div.top-novels").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst(".novel-title")?.text() ?: return@mapNotNull null

            newSearchResponse(title, href) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }
        }
    }
}