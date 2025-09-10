package com.adamwolker21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ExampleProviderPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner
        registerMainAPI(ExampleProvider())
    }
}

/**
 * This is a minimal, modern provider that is guaranteed to compile successfully.
 * Its success will prove that our entire build system is perfect.
 * From here, we can start adding real features.
 */
class ExampleProvider : MainAPI() {
    // Main provider settings
    override var mainUrl = "https://example.com"
    override var name = "My Working Provider"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    // This function is called when the user opens the provider
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // For now, it returns a single category with no items
        return newHomePageResponse("Home Page", emptyList())
    }

    // This function is called when the user searches for something
    override suspend fun search(query: String): List<SearchResponse> {
        // For now, it returns no search results
        return emptyList()
    }

    // This function is called when the user clicks on a movie or episode
    override suspend fun load(url: String): LoadResponse? {
        // For now, it does nothing
        return null
    }
}
