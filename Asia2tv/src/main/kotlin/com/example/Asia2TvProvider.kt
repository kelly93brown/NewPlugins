package com.example

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
    override val mainPage = mainPageOf(
        "/" to "الصفحة الرئيسية",
        "/movies" to "الأفلام",
        "/series" to "المسلسلات",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "$mainUrl${request.data.removeSuffix("/")}/page/$page/"
        } else {
            "$mainUrl${request.data}"
        }
        
        val document = app.get(url, headers = getHeaders()).document

        if (request.data == "/") {
            val homePageList = mutableListOf<HomePageList>()
            document.select("div.section-title").forEach { section ->
                val title = section.text().trim()
                val container = section.nextElementSibling()?.selectFirst("div.video-block-container")
                val items = container?.select("div.video-block")?.mapNotNull { it.toSearchResponse() } ?: emptyList()
                if (items.isNotEmpty()) {
                    homePageList.add(HomePageList(title, items))
                }
            }
            return HomePageResponse(homePageList)
        } else {
            val items = document.select("div.video-block").mapNotNull { it.toSearchResponse() }
            val hasNext = document.selectFirst("a.next") != null
            return newHomePageResponse(request.name, items, hasNext)
        }
    }
    
    private fun Element.toSearchResponse(): SearchResponse? {
        val link = this.selectFirst("a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val title = this.selectFirst("div.video-details h2")?.text()?.trim() ?: return null
        val poster = this.selectFirst("img")?.attr("src") ?: ""
        val type = if (link.contains("/movie/")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, link) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, link) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.encodeToUrl()}"
        val document = app.get(url, headers = getHeaders()).document
        return document.select("div.video-block").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = getHeaders(url)).document
        
        // Extract basic information
        val title = document.selectFirst("div.video-details h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.video-img img")?.attr("src") ?: ""
        val description = document.selectFirst("div.video-desc p")?.text()?.trim() ?: ""
        
        // Extract additional metadata
        val year = document.select("div.video-details p:contains(سنة)").firstOrNull()?.text()?.replace("سنة الإنتاج:", "")?.trim()?.toIntOrNull()
        val tags = document.select("div.video-details p:contains(النوع) a").map { it.text().trim() }
        val statusText = document.selectFirst("span.status")?.text()?.trim() ?: ""
        val isCompleted = statusText.contains("مكتمل") || statusText.contains("مكتملة")

        // Determine content type
        val isMovie = url.contains("/movie/") || document.selectFirst("div.video-details p:contains(نوع) a:contains(فيلم)") != null
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            // Extract episodes
            val episodes = document.select("div.video-episodes a").mapNotNull { episodeElement ->
                val episodeUrl = fixUrl(episodeElement.attr("href"))
                val episodeTitle = episodeElement.text().trim()
                val episodeNumber = extractEpisodeNumber(episodeTitle)
                
                newEpisode(episodeUrl) {
                    this.name = episodeTitle
                    this.episode = episodeNumber
                    this.season = 1 // Default season, can be improved
                }
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.status = if (isCompleted) ShowStatus.Completed else ShowStatus.Ongoing
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = getHeaders(data)).document
        
        // Extract server links from the video server buttons
        val serverElements = document.select("div.video-server button")
        
        coroutineScope {
            serverElements.map { serverButton ->
                async {
                    val serverName = serverButton.text().trim()
                    val serverUrl = serverButton.attr("data-url")
                    
                    if (serverUrl.isNotBlank()) {
                        val fullUrl = fixUrl(serverUrl)
                        loadExtractor(fullUrl, data, subtitleCallback, callback, serverName)
                    }
                }
            }.awaitAll()
        }
        
        return true
    }
    
    private fun getHeaders(referer: String = mainUrl): Map<String, String> {
        return mapOf(
            "Referer" to referer,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "ar,en-US;q=0.7,en;q=0.3",
            "Accept-Encoding" to "gzip, deflate",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1"
        )
    }
    
    private fun extractEpisodeNumber(title: String): Int? {
        val patterns = listOf(
            Regex("الحلقة\\s+(\\d+)"),
            Regex("Episode\\s+(\\d+)"),
            Regex("E(\\d+)"),
            Regex("(\\d+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        
        return null
    }
    
    private fun String.encodeToUrl(): String {
        return this.replace(" ", "+")
    }
}
