package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
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

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    data class PlayerResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: String
    )

    override val mainPage = mainPageOf(
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "$mainUrl${request.data}/page/$page/"
        } else {
            "$mainUrl${request.data}"
        }
        
        val document = app.get(url, headers = headers).document
        
        // v7 Fix: The site uses <article class="item">, not <div class="item">. This was the core issue.
        val items = document.select("div.items article.item").mapNotNull { it.toSearchResponse() }
        
        val hasNext = document.selectFirst("a.nextpostslink") != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        // v7 Fix: Using more specific selectors inside the item article
        val linkElement = this.selectFirst("div.poster a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        if (href.isBlank()) return null

        val title = this.selectFirst("div.data h3 a")?.text() ?: this.selectFirst("div.data h3")?.text() ?: return null
        val posterUrl = this.selectFirst("div.poster img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }

        val type = if (href.contains("/movie/")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = headers).document
        // v7 Fix: Applying the same <article> fix to search results
        return document.select("div.items article.item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = headers).document
        val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val plot = document.selectFirst("div.story p")?.text()?.trim()
        val year = document.select("div.details ul li a[href*=release]").firstOrNull()?.text()?.toIntOrNull()
        val tags = document.select("div.details ul li a[href*=genre]").map { it.text() }
        val rating = document.selectFirst("div.imdb span")?.text()?.toFloatOrNull()?.times(10)?.toInt()
        val recommendations = document.select("div.related div.item article.item").mapNotNull { it.toSearchResponse() }

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
        val document = app.get(data, headers = headers).document
        val serverIds = document.select("div.servers-list ul li").mapNotNull {
            it.attr("data-server").ifBlank { null }
        }

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        val ajaxHeaders = headers + mapOf("Referer" to data)

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
                        ).parsed<PlayerResponse>()

                        if (response.success) {
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
