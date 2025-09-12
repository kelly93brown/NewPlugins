package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll

// v15: Final, simplified, and correct implementation based on all findings.
// This version uses a single, smart `getMainPage` to handle both the main page and category pages.
class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Static list of categories, which acts as the main navigation.
    override val mainPage = mainPageOf(
        "/" to "الرئيسية",
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "$mainUrl${request.data}page/$page/"
        } else {
            "$mainUrl${request.data}"
        }
        val document = app.get(url).document

        // This is the core logic: we check which type of page we are on.
        if (request.data == "/") {
            // Logic for the TRUE main page (based on Asia2tv.com.html)
            val homePageList = mutableListOf<HomePageList>()
            document.select("div.mov-cat-d").forEach { block ->
                val title = block.selectFirst("h2.mov-cat-d-title")?.text() ?: return@forEach
                val items = block.select("article").mapNotNull { it.toSearchResponse(true) }
                if (items.isNotEmpty()) {
                    homePageList.add(HomePageList(title, items))
                }
            }
            return HomePageResponse(homePageList)
        } else {
            // Logic for CATEGORY pages (based on status live.html)
            val items = document.select("div.postmovie").mapNotNull { it.toSearchResponse(false) }
            return newHomePageResponse(request.name, items, true) // Assuming pagination exists
        }
    }
    
    // A single, smart helper function to parse items from ANY page.
    private fun Element.toSearchResponse(isHomePage: Boolean): SearchResponse? {
        val linkElement: Element?
        val title: String?
        
        if (isHomePage) {
            // Selectors for the main page <article> structure
            linkElement = this.selectFirst("h3.post-box-title a")
            title = linkElement?.text()
        } else {
            // Selectors for the category page <div.postmovie> structure
            linkElement = this.selectFirst("h4 > a")
            title = linkElement?.text()
        }

        val href = linkElement?.attr("href")?.let { fixUrl(it) } ?: return null
        if (title.isNullOrBlank()) return null

        val posterUrl = this.selectFirst("img")?.attr("data-src")

        return if (href.contains("/movie/")) {
            newMovieSearchResponse(title, href) { this.posterUrl = posterUrl }
        } else {
            newTvSeriesSearchResponse(title, href) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        // Search results use the category page structure.
        return document.select("div.postmovie").mapNotNull { it.toSearchResponse(false) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.name")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        val year = document.selectFirst("div.extra-info span:contains(سنة) a")?.text()?.toIntOrNull()
        val tags = document.select("div.extra-info span:contains(النوع) a").map { it.text() }

        return if (url.contains("/movie/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags
            }
        } else {
            val episodes = document.select("div#DivEpisodes a").mapNotNull { epElement ->
                val epHref = epElement.attr("data-url")
                if (epHref.isBlank()) return@mapNotNull null
                val epName = epElement.text().trim()
                val epNum = epName.filter { it.isDigit() }.toIntOrNull()
                newEpisode(epHref) { this.name = epName; this.episode = epNum }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
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
