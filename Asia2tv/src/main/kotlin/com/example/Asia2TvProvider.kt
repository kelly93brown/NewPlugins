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
        "/newepisode" to "الحلقات الجديدة",
        "/status/live" to "يبث حاليا",
        "/status/complete" to "أعمال مكتملة",
        "/series" to "المسلسلات",
        "/movies" to "الأفلام"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "$mainUrl${request.data.removeSuffix("/")}/page/$page/"
        } else {
            "$mainUrl${request.data}"
        }
        
        val document = app.get(url, headers = getHeaders()).document

        // استخدام المحددات الصحيحة بناءً على HTML الفعلي
        val homePageList = mutableListOf<HomePageList>()
        
        if (request.data == "/") {
            // للصفحة الرئيسية، نحاول استخراج الأقسام
            val sections = document.select("div.section, div.mov-cat-d")
            
            if (sections.isNotEmpty()) {
                sections.forEach { section ->
                    val title = section.selectFirst("h2, h3, div.section-title")?.text()?.trim() ?: "غير مصنف"
                    val items = section.select("div.postmovie, article.post, div.video-block").mapNotNull { 
                        it.toSearchResponse()
                    }
                    
                    if (items.isNotEmpty()) {
                        homePageList.add(HomePageList(title, items))
                    }
                }
            } else {
                // إذا لم نجد أقسامًا، نأخذ كل المحتوى
                val allItems = document.select("div.postmovie, article.post, div.video-block").mapNotNull { 
                    it.toSearchResponse() 
                }
                
                if (allItems.isNotEmpty()) {
                    homePageList.add(HomePageList("أحدث المحتوى", allItems))
                }
            }
        } else {
            // للصفحات الأخرى
            val items = document.select("div.postmovie, article.post, div.video-block").mapNotNull { 
                it.toSearchResponse() 
            }
            
            if (items.isNotEmpty()) {
                homePageList.add(HomePageList(request.name, items))
            }
        }
        
        val hasNext = document.selectFirst("a.next, a.page-link:contains(التالي)") != null
        return newHomePageResponse(request.name, homePageList, hasNext)
    }
    
    private fun Element.toSearchResponse(): SearchResponse? {
        // استخراج الرابط من عناصر مختلفة محتملة
        val linkElement = this.selectFirst("h3 a, h4 a, h2 a, a.thumbnail")
        val href = linkElement?.attr("href")?.let { fixUrl(it) } 
                  ?: this.selectFirst("a")?.attr("href")?.let { fixUrl(it) } 
                  ?: return null
        
        // استخراج العنوان من عناصر مختلفة محتملة
        val title = linkElement?.text()?.trim() 
                  ?: this.selectFirst("h2, h3, h4, div.title")?.text()?.trim()
                  ?: this.attr("title")
                  ?: return null
        
        // استخراج الصورة من عناصر مختلفة محتملة
        val poster = this.selectFirst("img")?.let { 
            it.attr("src") ?: it.attr("data-src") 
        } ?: ""
        
        // تحديد نوع المحتوى
        val isMovie = href.contains("/movie/") || title.contains("فيلم")
        
        return if (isMovie) {
            newMovieSearchResponse(title, href) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url, headers = getHeaders()).document
        
        return document.select("div.postmovie, article.post, div.search-result").mapNotNull { 
            it.toSearchResponse() 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = getHeaders(url)).document
        
        // استخراج المعلومات الأساسية
        val title = document.selectFirst("h1, h1.name, div.video-details h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img, div.video-img img, img.wp-post-image")?.attr("src") ?: ""
        val description = document.selectFirst("div.story, div.video-desc, div.description")?.text()?.trim() ?: ""
        
        // استخراج المعلومات الإضافية
        val year = document.select("div.extra-info:contains(سنة), p:contains(سنة)").firstOrNull()?.text()?.let {
            Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }
        
        val tags = document.select("div.extra-info:contains(النوع) a, p:contains(النوع) a, a[href*=genre]").map { it.text().trim() }
        
        // تحديد نوع المحتوى
        val isMovie = url.contains("/movie/") || document.selectFirst(":contains(فيلم)") != null
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            // استخراج الحلقات
            val episodes = document.select("div#DivEpisodes a, div.video-episodes a, a[href*=/episode/]").mapNotNull { episodeElement ->
                val episodeUrl = fixUrl(episodeElement.attr("href"))
                val episodeTitle = episodeElement.text().trim()
                val episodeNumber = extractEpisodeNumber(episodeTitle)
                
                newEpisode(episodeUrl) {
                    this.name = episodeTitle
                    this.episode = episodeNumber
                    this.season = 1
                }
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
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
        val document = app.get(data, headers = getHeaders(data)).document
        
        // استخراج روابط الخوادم
        val serverElements = document.select("div.video-server button, div.server-item button, button[data-url]")
        
        coroutineScope {
            serverElements.map { serverButton ->
                async {
                    val serverName = serverButton.text().trim()
                    val serverUrl = serverButton.attr("data-url")
                    
                    if (serverUrl.isNotBlank()) {
                        val fullUrl = fixUrl(serverUrl)
                        loadExtractor(fullUrl, data, subtitleCallback, callback)
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
}
