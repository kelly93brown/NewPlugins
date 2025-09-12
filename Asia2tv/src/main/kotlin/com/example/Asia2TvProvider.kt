package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.toRatingInt
import org.jsoup.nodes.Element
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll

class Asia2Tv : MainAPI() {
    override var name = "Asia2Tv"
    override var mainUrl = "https://asia2tv.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Data class to parse the JSON response for server iframes
    data class PlayerResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: String
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return HomePageResponse(emptyList())

        val document = app.get(mainUrl).document
        val allhome = mutableListOf<HomePageList>()

        document.select("div.content-box").forEach { section ->
            val title = section.selectFirst("div.block_title h2 a")?.text() ?: return@forEach
            // Filter out sections that are not for movies or series
            if (title.contains("نجوم") || title.contains("أخبار")) return@forEach

            val items = section.select("div.items div.item").mapNotNull { it.toSearchResponse() }
            if (items.isNotEmpty()) {
                allhome.add(HomePageList(title, items))
            }
        }
        return HomePageResponse(allhome)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        if (href.isBlank()) return null

        val title = this.selectFirst("div.data h3")?.text() ?: return null
        val posterUrl = this.selectFirst("div.poster img")?.let {
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
        return document.select("div.items div.item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.story p")?.text()?.trim()
        val year = document.select("div.details ul li a[href*=release]").firstOrNull()?.text()?.toIntOrNull()
        val tags = document.select("div.details ul li a[href*=genre]").map { it.text() }
        val rating = document.selectFirst("div.imdb span")?.text()?.toRatingInt()
        val recommendations = document.select("div.related div.item").mapNotNull { it.toSearchResponse() }

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
            document.select("div.episodes-list").forEach { seasonList ->
                val seasonId = seasonList.attr("id")
                val seasonNum = seasonId.substringAfter("season-").toIntOrNull()

                seasonList.select("ul.episodes-list-content li").forEach { episodeElement ->
                    val epLink = episodeElement.selectFirst("a") ?: return@forEach
                    val epHref = fixUrl(epLink.attr("href"))
                    val epName = episodeElement.selectFirst("div.data h3")?.text()?.trim() ?: "Episode"
                    val epNum = epName.filter { it.isDigit() }.toIntOrNull()

                    episodes.add(newEpisode(epHref) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = epNum
                        this.posterUrl = episodeElement.selectFirst("div.poster img")?.let {
                            it.attr("data-src").ifBlank { it.attr("src") }
                        }
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
        val serverIds = document.select("div.servers-list ul li").mapNotNull {
            it.attr("data-server").ifBlank { null }
        }

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"

        // The website loads the player iframe using an AJAX call.
        // We must replicate this call to get the iframe source URL.
        coroutineScope {
            serverIds.map { serverId ->
                async {
                    try {
                        val response = app.post(
                            ajaxUrl,
                            headers = mapOf("Referer" to data),
                            data = mapOf(
                                "action" to "get_player_content",
                                "server" to serverId
                            )
                        ).parsed<PlayerResponse>()

                        if (response.success) {
                            // The response data contains the full iframe HTML, we extract the src
                            val iframeSrc = Regex("""src=["'](.*?)["']""").find(response.data)?.groupValues?.get(1)
                            if (iframeSrc != null) {
                                loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
                            }
                        }
                    } catch (_: Exception) {
                        // Suppress exceptions to allow other servers to load
                    }
                }
            }.awaitAll()
        }
        return true
    }
}
