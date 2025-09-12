package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Kept your headers, it's good practice.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )
    
    // Using your original mainPage structure as it's efficient.
    override val mainPage = mainPageOf(
        "/newepisode" to "أحدث الحلقات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة",
        "/movies" to "الأفلام",
        "/series" to "المسلسلات"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "$mainUrl${request.data}/page/$page" else "$mainUrl${request.data}"
        val document = app.get(url, headers = headers).document

        // FIX: The site structure changed. It no longer uses "div.items article.item".
        // For category pages, it uses "div.postmovie".
        // For the main page, it uses "article". We select both to be safe.
        val items = document.select("div.postmovie, article").mapNotNull { it.toSearchResponse() }
        
        // The logic for next page seems to have changed, we'll assume there's always more to load.
        // A more robust solution would be to check for a specific "next" button if it exists.
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        // FIX: Selectors are updated to match the new structure of both <article> and <div.postmovie>
        val linkElement = this.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        if (href.isBlank() || href == mainUrl) return null

        val title = this.selectFirst("h3.post-box-title, h4 > a")?.text() ?: return null
        
        // Site uses lazy loading with "data-src"
        val posterUrl = this.selectFirst("img")?.attr("data-src")

        // Determine type based on URL structure, "/serie/" is more reliable than "/movie/"
        val type = if (href.contains("/serie/")) TvType.TvSeries else TvType.Movie

        return newTvShowSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = headers).document
        
        // FIX: Search results now use <article> tags directly.
        return document.select("article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = headers).document
        val title = document.selectFirst("h1.name")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        val year = document.select("ul.info li:contains(سنة الإنتاج) a").text().toIntOrNull()
        val tags = document.select("div.genres-single a[href*=genre]").map { it.text() }
        val ratingText = document.selectFirst("span.rating-vote")?.text()
        val rating = ratingText?.let { Regex("""(\d\.\d)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }?.times(10)?.toInt()
        val recommendations = document.select("div.content-box article").mapNotNull { it.toSearchResponse() }

        val isTvSeries = url.contains("/serie/")
        
        if (isTvSeries) {
            // FIX: Episodes are now in a simple list of links within "div#DivEpisodes".
            val episodes = document.select("div#DivEpisodes a").mapNotNull { episodeElement ->
                val epUrl = episodeElement.attr("data-url").ifBlank { return@mapNotNull null }
                val epName = episodeElement.text().trim()
                
                newEpisode(epUrl) {
                    this.name = epName
                }
            }.reversed() // Reverse to show Episode 1 first.

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
            }
        } else { // It's a movie
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // FIX: BIG CHANGE! The site no longer uses the complex AJAX system.
        // It now embeds server iframes directly in the episode/movie page. This is much simpler.
        // The 'data' variable is the URL of the episode page we get from the 'load' function.
        val document = app.get(data, headers = headers).document

        document.select("iframe").apmap { // Using apmap for parallel loading
            val iframeSrc = it.attr("src")
            if (iframeSrc.isNotBlank()) {
                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            }
        }
        
        return true
    }
}
