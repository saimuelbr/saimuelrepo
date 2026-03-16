package com.EmbedTVOnline

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

class EmbedTVOnline : MainAPI() {
    override var name = "EmbedTVOnline"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("https://embedtvonline.com/").document
        
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

        val doc = app.get("https://embedtvonline.com/").document
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
        val doc = app.get("https://embedtvonline.com/").document
        
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
    val response = app.get(channelUrl)
    val doc = response.document

    val scripts = doc.select("script")
    var finalUrl: String? = null

    for (script in scripts) {
        val scriptContent = script.html()
        if (scriptContent.contains("const SRC")) {
            val urlMatch = Regex(
                """const\s+SRC\s*=\s*q\(\s*['"]src['"]\s*,\s*['"]([^'"]+\.m3u8[^'"]*)['"]\)"""
            ).find(scriptContent)

            if (urlMatch != null) {
                finalUrl = urlMatch.groupValues[1]
                break
            }
        }
    }

    if (finalUrl == null) {
        val regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
        finalUrl = regex.find(doc.html())?.value
    }

    if (finalUrl == null) return false

    val headers = mapOf(
        "Origin" to "https://embedcanaisonline.com",
        "Referer" to "https://embedcanaisonline.com/",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
        "Accept" to "*/*",
        "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Dest" to "empty",
        "DNT" to "1",
        "Sec-GPC" to "1"
    )

        callback(newExtractorLink("EmbedTVOnline", "EmbedTVOnline Live", finalUrl) {
                this.referer = channelUrl
            this.type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
            this.headers = headers
            })

            return true
    }

}