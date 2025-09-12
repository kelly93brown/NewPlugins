package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll

// Final Corrected Version: All selectors and logic have been updated
// based on the provided HTML files. The original advanced structure is preserved.
class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // NOTE: This data class is no longer used because the site has switched
    // from an AJAX-based player system to simple iframes.
    // It's kept here to show the evolution but is not called.
    data class PlayerResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: String
    )
    
    // EVIDENCE-BASED: These are the correct main sections.
    // I've added the homepage ("/") as it has a unique structure.
    override val mainPage = mainPageOf(
        "/" to "الصفحة الرئيسية",
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "$mainUrl${request.data}page/$page/" else "$mainUrl${request.data}"
        val document = app.get(url).document

        // FIX: The site uses a hybrid structure.
        // `article` for the main page ("/") and `div.postmovie` for category pages.
        // This selector now correctly handles both cases.
        val items = document.select("article, div.postmovie").mapNotNull { it.toSearchResponse() }
        
        // The site's pagination link seems to be gone, so we'll remove the hasNext logic
        // to prevent potential bugs. The app will handle infinite scrolling.
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        // FIX: This function is now intelligent enough to parse both structures.
        // It tries to find the title/link in either `h3` (for main page) or `h4` (for categories).
        val linkElement = this.selectFirst("h3.post-box-title a, h4 > a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.text()

        // FIX: The correct image attribute is 'data-src'.
        val posterUrl = this.selectFirst("img")?.attr("data-src")

        if (href.isBlank() || title.isBlank()) return null
        
        val tvType = if (href.contains("/serie/")) TvType.TvSeries else TvType.Movie
        
        // Kept your original response structure.
        return newTvShowSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        // FIX: Search results page uses <article> tags, not "article.item".
        return document.select("article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // FIX: All selectors updated based on 'serie tempest.html' and your original advanced structure.
        val title = document.selectFirst("h1.name")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        val year = document.select("ul.info li:contains(سنة الإنتاج) a").text().toIntOrNull()
        val tags = document.select("div.genres-single a[href*=genre]").map { it.text() }
        val ratingText = document.selectFirst("span.rating-vote")?.text()
        val rating = ratingText?.let { Regex("""(\d\.\d)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }?.times(10)?.toInt()
        val recommendations = document.select("div.content-box article").mapNotNull { it.toSearchResponse() }

        return if (url.contains("/movie/")) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
            }
        } else {
            // FIX: Major logic change for episodes based on 'serie tempest.html'.
            // The old season logic is replaced with the correct episode list parsing.
            val episodes = document.select("div#DivEpisodes a").mapNotNull {
                val epName = it.text()
                // EVIDENCE: The real episode link is in the 'data-url' attribute.
                val epUrl = it.attr("data-url").ifBlank { return@mapNotNull null }
                
                newEpisode(epUrl) { this.name = epName }
            }.reversed() // Reverse to show Episode 1 first.

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        // FIX: Complete overhaul of this function's logic.
        // EVIDENCE: 'Ep1.html' proves the site uses simple iframes, not the old AJAX system.
        // We will keep the advanced concurrent loading using `apmap`.
        val document = app.get(data).document
        
        document.select("iframe").apmap { iframe -> // apmap is a modern way to do async mapping
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotBlank()) {
                // This Cloudstream utility function handles the rest.
                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
            }
        }
        
        return true
    }
}
