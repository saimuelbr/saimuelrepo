package com.EmbedCanais

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

class EmbedCanais : MainAPI() {
    override var name = "EmbedCanais"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("https://embedcanais.com/").document
        
        val cards = doc.select("main.grid div.card")
        val channels = cards.mapNotNull { card ->
            val title = card.selectFirst("div.title")?.text()?.trim()
            val url = card.selectFirst("a.thumb")?.attr("href")
            val img = card.selectFirst("a.thumb img")?.attr("src")
            
            if (title != null && url != null && img != null) {
                newLiveSearchResponse(title, url, TvType.Live) {
                    this.posterUrl = img
                }
            } else null
        }
        
        return newHomePageResponse(
            listOf(
                HomePageList("Todos os Canais", channels, isHorizontalImages = true)
            )
        )
    }

    override suspend fun load(data: String): LoadResponse {
        val channelUrl = data.ifEmpty { throw ErrorLoadingException("Invalid Json reponse") }

        val doc = app.get("https://embedcanais.com/").document
        val channelCard = doc.select("main.grid div.card").find { card ->
            card.selectFirst("a.thumb")?.attr("href") == channelUrl
        }
        
        val channelName = channelCard?.selectFirst("div.title")?.text()?.trim() ?: "Canal"
        val channelPoster = channelCard?.selectFirst("a.thumb img")?.attr("src")

        return newMovieLoadResponse(
            name = "Canal $channelName",
            url = channelUrl,
            type = TvType.Live,
            channelUrl
        ) {
            this.posterUrl = channelPoster
        }
    }

    override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse>? {
        val doc = app.get("https://embedcanais.com/").document
        
        val cards = doc.select("main.grid div.card")
        val results = cards.mapNotNull { card ->
            val title = card.selectFirst("div.title")?.text()?.trim()
            val url = card.selectFirst("a.thumb")?.attr("href")
            val img = card.selectFirst("a.thumb img")?.attr("src")
            
            if (title != null && url != null && img != null && 
                title.contains(query, ignoreCase = true)) {
                newLiveSearchResponse(title, url, TvType.Live) {
                    this.posterUrl = img
                }
            } else null
        }
        
        return results
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val channelUrl = data.ifEmpty { return false }
    val doc = app.get(channelUrl).document

    val scripts = doc.select("script")
    var finalUrl: String? = null

    for (script in scripts) {
        val scriptContent = script.html()

        val urlMatch = Regex(
            """(?:const\s+(?:url|SRC)\s*=\s*(?:q\(['"]src['"],\s*)?['"]([^'"]+\.m3u8[^'"]*)['"])"""
        ).find(scriptContent)

        if (urlMatch != null) {
            finalUrl = urlMatch.groupValues[1]
            break
        }
    }

    if (finalUrl == null) {
        val html = doc.html()
        val regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
        finalUrl = regex.find(html)?.value
    }

    if (finalUrl == null) return false

        val headers = mapOf(
            "referer" to "https://embedcanais.com/",
            "origin" to "https://embedcanais.com",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "accept" to "application/vnd.apple.mpegurl",
            "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-gpc" to "1",
            "dnt" to "1"
        )

        callback(newExtractorLink("EmbedCanais", "EmbedCanais Live", finalUrl) {
                this.referer = channelUrl
            this.type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
            this.headers = headers
            })

            return true
    }

}