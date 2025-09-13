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

        // نهج جديد لاستخراج المحتوى - أكثر مرونة مع الهيكل الحقيقي للموقع
        if (request.data == "/") {
            val homePageList = mutableListOf<HomePageList>()
            
            // البحث عن جميع الأقسام في الصفحة الرئيسية
            val sections = document.select("div.section")
            if (sections.isNotEmpty()) {
                sections.forEach { section ->
                    val titleElement = section.selectFirst("h2.section-title, div.section-title")
                    val title = titleElement?.text()?.trim() ?: "غير مصنف"
                    
                    val items = section.select("div.video-block, article.post, div.postmovie").mapNotNull { 
                        it.toSearchResponse() 
                    }
                    
                    if (items.isNotEmpty()) {
                        homePageList.add(HomePageList(title, items))
                    }
                }
            } else {
                // إذا لم نجد أقسامًا بالطريقة الأولى، نبحث عن أي محتوى مباشرة
                val items = document.select("div.video-block, article.post, div.postmovie").mapNotNull { 
                    it.toSearchResponse() 
                }
                if (items.isNotEmpty()) {
                    homePageList.add(HomePageList("أحدث المحتوى", items))
                }
            }
            
            return HomePageResponse(homePageList)
        } else {
            // للصفحات الأخرى (الأفلام، المسلسلات، إلخ)
            val items = document.select("div.video-block, article.post, div.postmovie").mapNotNull { 
                it.toSearchResponse() 
            }
            val hasNext = document.selectFirst("a.next, a.page-link:contains(التالي)") != null
            return newHomePageResponse(request.name, items, hasNext)
        }
    }
    
    private fun Element.toSearchResponse(): SearchResponse? {
        // محاولة استخراج الرابط بطرق متعددة
        val linkElement = this.selectFirst("a")
        val href = linkElement?.attr("href")?.let { fixUrl(it) } ?: return null
        
        // محاولة استخراج العنوان بطرق متعددة
        val title = this.selectFirst("h2, h3, h4, div.video-details h2, div.title")?.text()?.trim() 
                  ?: linkElement.attr("title") 
                  ?: return null
        
        // محاولة استخراج الصورة بطرق متعددة
        val poster = this.selectFirst("img")?.let { 
            it.attr("src") ?: it.attr("data-src") 
        } ?: ""
        
        // تحديد نوع المحتوى بناءً على الرابط أو العناصر الأخرى
        val isMovie = href.contains("/movie/") || this.selectFirst("span:contains(فيلم), i.fa-film") != null
        
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
        val url = "$mainUrl/?s=${query.encodeToUrl()}"
        val document = app.get(url, headers = getHeaders()).document
        
        // استخدام نهج متعدد للبحث عن النتائج
        return document.select("div.video-block, article.post, div.postmovie, div.search-result").mapNotNull { 
            it.toSearchResponse() 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = getHeaders(url)).document
        
        // استخراج المعلومات الأساسية بطرق متعددة
        val title = document.selectFirst("h1, h1.name, div.video-details h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("img.poster, div.poster img, div.video-img img")?.attr("src") ?: ""
        val description = document.selectFirst("div.story, div.video-desc, div.description")?.text()?.trim() ?: ""
        
        // استخراج المعلومات الإضافية
        val year = extractYear(document)
        val tags = document.select("a[href*=genre], a[href*=type], span.genre a").map { it.text().trim() }
        
        // تحديد نوع المحتوى
        val isMovie = url.contains("/movie/") || document.selectFirst(":contains(فيلم), :contains(فيلم)") != null
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            // استخراج الحلقات بطرق متعددة
            val episodes = document.select("div.episode-list a, div.video-episodes a, a[href*=/episode/], a.episode").mapNotNull { episodeElement ->
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
        
        // استخراج روابط الخوادم بطرق متعددة
        val serverElements = document.select("div.video-server button, div.server-item, button.server-btn")
        
        coroutineScope {
            serverElements.map { serverButton ->
                async {
                    val serverName = serverButton.text().trim()
                    val serverUrl = serverButton.attr("data-url").takeIf { it.isNotBlank() } 
                                  ?: serverButton.attr("data-src")
                                  ?: serverButton.attr("data-link")
                    
                    if (!serverUrl.isNullOrBlank()) {
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
    
    private fun extractYear(document: org.jsoup.nodes.Document): Int? {
        val yearText = document.selectFirst(":contains(سنة), :contains(عام), :contains(year)")?.text()
        return yearText?.let { 
            Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() 
        }
    }
    
    private fun extractEpisodeNumber(title: String): Int? {
        val patterns = listOf(
            Regex("الحلقة\\s*(\\d+)"),
            Regex("Episode\\s*(\\d+)"),
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
