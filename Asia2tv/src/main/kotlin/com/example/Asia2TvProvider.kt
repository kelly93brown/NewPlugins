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
            // نهج مختلف لاستخراج المحتوى من الصفحة الرئيسية
            val homePageList = mutableListOf<HomePageList>()
            
            // البحث عن جميع الأقسام في الصفحة الرئيسية
            val sections = document.select("div.section")
            
            if (sections.isNotEmpty()) {
                sections.forEach { section ->
                    val titleElement = section.selectFirst("h2.section-title, div.section-title")
                    val title = titleElement?.text()?.trim() ?: "غير مصنف"
                    
                    val items = section.select("div.video-block").mapNotNull { 
                        it.toSearchResponseFromVideoBlock()
                    }
                    
                    if (items.isNotEmpty()) {
                        homePageList.add(HomePageList(title, items))
                    }
                }
            } else {
                // إذا لم نجد أقسامًا، نبحث عن المحتوى مباشرة
                val allItems = document.select("div.video-block").mapNotNull { 
                    it.toSearchResponseFromVideoBlock() 
                }
                
                if (allItems.isNotEmpty()) {
                    homePageList.add(HomePageList("أحدث المحتوى", allItems))
                }
            }
            
            return HomePageResponse(homePageList)
        } else {
            // للصفحات الأخرى (الأفلام، المسلسلات، إلخ)
            val items = document.select("div.video-block").mapNotNull { 
                it.toSearchResponseFromVideoBlock() 
            }
            val hasNext = document.selectFirst("a.next") != null
            return newHomePageResponse(request.name, items, hasNext)
        }
    }
    
    // دالة خاصة لاستخراج المحتوى من عناصر video-block
    private fun Element.toSearchResponseFromVideoBlock(): SearchResponse? {
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
    
    // دالة للعناصر الأخرى (للبحث)
    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("h3 a, h4 a, h2 a")
        val href = linkElement?.attr("href")?.let { fixUrl(it) } ?: return null
        val title = linkElement.text().trim()
        val poster = this.selectFirst("img")?.attr("src") ?: this.selectFirst("img")?.attr("data-src") ?: ""
        val type = if (href.contains("/movie/")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
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
        
        // البحث في نتائج البحث
        return document.select("article.post, div.postmovie, div.search-result").mapNotNull { 
            it.toSearchResponse() 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = getHeaders(url)).document
        
        // استخراج المعلومات الأساسية
        val title = document.selectFirst("h1.name, div.video-details h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.poster img, div.video-img img")?.attr("src") ?: ""
        val description = document.selectFirst("div.story, div.video-desc p")?.text()?.trim() ?: ""
        
        // استخراج المعلومات الإضافية
        val year = document.select("div.extra-info:contains(سنة), p:contains(سنة)").firstOrNull()?.text()?.let {
            Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }
        
        val tags = document.select("div.extra-info:contains(النوع) a, p:contains(النوع) a").map { it.text().trim() }
        
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
            val episodes = document.select("div#DivEpisodes a, div.video-episodes a").mapNotNull { episodeElement ->
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
        val serverElements = document.select("div.video-server button")
        
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
            "Accept-Language" to "ar,
