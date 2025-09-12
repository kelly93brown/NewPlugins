package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll

// v14: The definitive version, meticulously crafted based on the user's HTML evidence.
// This version correctly handles the different structures of the main page vs. category pages.
class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Helper for Main Page items, which use <article>
    private fun Element.toMainPageSearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("h3.post-box-title a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text()
        val posterUrl = this.selectFirst("img")?.attr("data-src")

        return if (href.contains("/movie/")) {
            newMovieSearchResponse(title, href) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href) { this.posterUrl = posterUrl }
        }
    }

    // Helper for Category Page items, which use <div.postmovie>
    private fun Element.toCategorySearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("h4 > a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text()
        val posterUrl = this.selectFirst("img")?.attr("data-src")

        return if (href.contains("/movie/")) {
            newMovieSearchResponse(title, href) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href) { this.posterUrl = posterUrl }
        }
    }

    // This function now correctly scrapes the sections from the true main page.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return HomePageResponse(emptyList()) // Main page has no pagination
        
        val document = app.get(mainUrl).document
        val homePageList = mutableListOf<HomePageList>()

        document.select("div.mov-cat-d").forEach { block ->
            val title = block.selectFirst("h2.mov-cat-d-title")?.text() ?: return@forEach
            val items = block.select("article").mapNotNull { it.toMainPageSearchResponse() }
            if (items.isNotEmpty()) {
                homePageList.add(HomePageList(title, items))
            }
        }
        return HomePageResponse(homePageList)
    }

    // Static list of categories
    override val mainPage = mainPageOf(
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    // This function is triggered when a user clicks on a category from the list above.
    override suspend fun loadPage(
        page: Int,
        source: String,
        type: TvType,
        listName: String
    ): List<SearchResponse> {
        val url = if (page > 1) "$mainUrl$source/page/$page/" else "$mainUrl$source"
        val document = app.get(url).document
        // It uses the correct parser for category pages.
        return document.select("div.postmovie").mapNotNull { it.toCategorySearchResponse() }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        // Search results page uses the category page structure.
        return document.select("div.postmovie").mapNotNull { it.toCategorySearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.name")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        
        // Corrected selectors for metadata based on "serie tempest.html"
        val year = document.selectFirst("div.extra-info span:contains(سنة) a")?.text()?.toIntOrNull()
        val tags = document.select("div.extra-info span:contains(النوع) a").map { it.text() }

        return if (url.contains("/movie/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        } else {
            // Correct episode logic: iterating through `div#DivEpisodes` and getting `data-url`.
            val episodes = document.select("div#DivEpisodes a").mapNotNull { epElement ->
                val epHref = epElement.attr("data-url")
                if (epHref.isBlank()) return@mapNotNull null
                val epName = epElement.text().trim()
                val epNum = epName.filter { it.isDigit() }.toIntOrNull()

                newEpisode(epHref) {
                    this.name = epName
                    this.episode = epNum
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Simplified and correct logic based on "Ep1.html". No AJAX needed.
        val document = app.get(data).document
        val iframes = document.select("iframe")

        coroutineScope {
            iframes.map { iframe ->
                async {
                    val iframeSrc = fixUrl(iframe.attr("src"))
                    if (iframeSrc.isNotBlank()) {
                        loadExtractor(iframeSrc, data, subtitleCallback, callback)
                    }
                }
            }.awaitAll()
        }
        return true
    }
}
