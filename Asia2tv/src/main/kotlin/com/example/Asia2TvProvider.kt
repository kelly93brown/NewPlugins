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

// v10: Complete rebuild based on user-provided HTML files.
class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    data class PlayerResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: String
    )

    // Re-implementing getMainPage to scrape dynamic sections from the homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val homePageList = mutableListOf<HomePageList>()

        // Find all content blocks on the main page based on the provided HTML structure
        document.select("div.mov-cat-d").forEach { block ->
            val title = block.selectFirst("h2.mov-cat-d-title")?.text() ?: return@forEach
            // The items are inside div.postmovie, which was the key discovery
            val items = block.select("div.postmovie").mapNotNull { it.toSearchResponse() }
            if (items.isNotEmpty()) {
                homePageList.add(HomePageList(title, items))
            }
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        // This function is now tailored to the `div.postmovie` structure
        val linkElement = this.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        if (href.isBlank()) return null

        val title = this.selectFirst("h3.postmovie-title a")?.text() ?: return null
        val posterUrl = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }

        return if (href.contains("/movie/")) {
            newMovieSearchResponse(title, href) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        // Search results also use a similar structure, but inside article.item
        return document.select("article.item").mapNotNull {
            val linkElement = it.selectFirst("div.poster a") ?: return@mapNotNull null
            val href = fixUrl(linkElement.attr("href"))
            val title = it.selectFirst("div.data h3 a")?.text() ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("data-src")

            if (href.contains("/movie/")) {
                newMovieSearchResponse(title, href) { this.posterUrl = posterUrl }
            } else {
                newTvSeriesSearchResponse(title, href) { this.posterUrl = posterUrl }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.story p")?.text()?.trim()
        val year = document.select("div.details ul.meta li:contains(سنة) a")?.text()?.toIntOrNull()
        val tags = document.select("div.details ul.meta li:contains(النوع) a").map { it.text() }
        val rating = document.selectFirst("div.imdb span")?.text()?.toFloatOrNull()?.times(10)?.toInt()
        val recommendations = document.select("div.related div.items article.item").mapNotNull {
            val linkElement = it.selectFirst("div.poster a") ?: return@mapNotNull null
            val href = fixUrl(linkElement.attr("href"))
            val recTitle = it.selectFirst("div.data h3 a")?.text() ?: return@mapNotNull null
            val recPosterUrl = it.selectFirst("img")?.attr("data-src")

            if (href.contains("/movie/")) {
                newMovieSearchResponse(recTitle, href) { this.posterUrl = recPosterUrl }
            } else {
                newTvSeriesSearchResponse(recTitle, href) { this.posterUrl = recPosterUrl }
            }
        }


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
            val episodes = mutableListOf<Episode>()
            // Episode parsing logic confirmed correct by the provided HTML
            document.select("div#seasons div.se-c").forEach { seasonElement ->
                val seasonName = seasonElement.selectFirst("h3")?.text() ?: ""
                val seasonNum = seasonName.filter { it.isDigit() }.toIntOrNull()

                seasonElement.select("ul.episodes li").forEach { episodeElement ->
                    val epLink = episodeElement.selectFirst("a") ?: return@forEach
                    val epHref = fixUrl(epLink.attr("href"))
                    val epName = epLink.text().trim()
                    val epNum = epName.filter { it.isDigit() }.toIntOrNull()

                    episodes.add(newEpisode(epHref) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = epNum
                    })
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
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
        val document = app.get(data).document
        // Server list logic confirmed correct by the provided HTML
        val serverIds = document.select("div.servers-list ul li").mapNotNull {
            it.attr("data-server").ifBlank { null }
        }

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        val ajaxHeaders = mapOf("Referer" to data)

        coroutineScope {
            serverIds.map { serverId ->
                async {
                    try {
                        val response = app.post(
                            ajaxUrl,
                            headers = ajaxHeaders,
                            data = mapOf(
                                "action" to "get_player_content",
                                "server" to serverId
                            )
                        ).text

                        val parsed = parseJson<PlayerResponse>(response)

                        if (parsed.success) {
                            val iframeSrc = Regex("""src=["'](.*?)["']""").find(parsed.data)?.groupValues?.get(1)
                            if (iframeSrc != null) {
                                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
                            }
                        }
                    } catch (_: Exception) {
                        // Suppress exceptions
                    }
                }
            }.awaitAll()
        }
        return true
    }
}
