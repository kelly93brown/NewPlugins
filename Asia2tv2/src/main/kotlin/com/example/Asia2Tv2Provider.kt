package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.Coroutines.apmap

// Final Working Build: This version fixes BOTH the private Score constructor issue AND
// the 'Unresolved reference posterUrl' error by passing it as a named parameter.
class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    
    override val mainPage = mainPageOf(
        "/" to "الصفحة الرئيسية",
        "/newepisode" to "أحدث الحلقات",
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "$mainUrl${request.data}page/$page/" else "$mainUrl${request.data}"
        val document = app.get(url).document

        val items = document.select("article, div.postmovie").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("h3.post-box-title a, h4 > a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text()
        val posterUrl = this.selectFirst("img")?.attr("data-src")

        if (href.isBlank() || title.isBlank()) return null
        
        val tvType = if (href.contains("/serie/")) TvType.TvSeries else TvType.Movie
        
        // BUILD FIX #2: `posterUrl` is now a named argument, not inside the lambda.
        return newTvShowSearchResponse(title, href, tvType, posterUrl = posterUrl)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        
        return document.select("article").mapNotNull { article ->
            val linkElement = article.selectFirst("h3.post-box-title a") ?: return@mapNotNull null
            val href = fixUrl(linkElement.attr("href"))
            val title = linkElement.text()
            val posterUrl = article.selectFirst("img")?.attr("data-src")
            val tvType = if (href.contains("/serie/")) TvType.TvSeries else TvType.Movie

            // BUILD FIX #2: Applied the same fix here for the search results.
            newTvShowSearchResponse(title, href, tvType, posterUrl = posterUrl)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.name")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        val year = document.select("ul.info li:contains(سنة الإنتاج) a").text().toIntOrNull()
        val tags = document.select("div.genres-single a[href*=genre]").map { it.text() }
        val recommendations = document.select("div.content-box article").mapNotNull { it.toSearchResult() }
        
        val ratingText = document.selectFirst("span.rating-vote")?.text()
        // BUILD FIX #1: Using the correct public constructor `Score(score = ...)`.
        val score = ratingText?.let { Regex("""(\d\.\d)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }?.times(100)?.toInt()?.let { Score(score = it) }

        return if (url.contains("/movie/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags; this.score = score; this.recommendations = recommendations
            }
        } else {
            val episodes = document.select("div#DivEpisodes a").mapNotNull {
                val epName = it.text()
                val epUrl = it.attr("data-url").ifBlank { return@mapNotNull null }
                newEpisode(epUrl) { this.name = epName }
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags; this.score = score; this.recommendations = recommendations
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
        
        document.select("iframe").apmap { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotBlank()) {
                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            }
        }
        
        return true
    }
}
