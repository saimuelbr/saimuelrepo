package com.GoFlix

import com.lagradost.api.Log
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.net.URLEncoder

class GoFlix : MainAPI() {
    override var mainUrl = "https://goflixy.lol"
    override var name = "GoFlix"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        private const val FEMBED_DOMAIN = "fembed.sx"
        private const val API_COOKIE = "SITE_TOTAL_ID=aNMeQg3ajIMkDqsskT-8twAAAMg; cf_clearance=1cz1gt_lNTNk3FBQfipe2ZMywqRJuCT98Irqbmy3dCk-1758666307-1.2.1.1-8iqgHQO5yglQC.QdLgffecdiDEoQueXo3bMTtXYg3b3k2V3zHUvF_RTUB9m5VGmPjkJmhWXufohjocVGUJix0YlTLOiywrHzz.yPhI.Epn05b1acy9t_iDQY34TbcpwVynI0c7qMS4HiKbfinTzPS.z0SREH9aFBkay.AfmYN6eFFkkonzbO5gBpEgzGZ_a6zjYgTVD_WmkOdM91YFvlR4p_6eGEa0Lq_J2fgHbPC2o"
    }

    override val mainPage = mainPageOf(
        "lancamentos" to "Lançamentos",
        "categoria/acao" to "Ação",
        "categoria/animacao" to "Animação",
        "categoria/comedia" to "Comédia",
        "categoria/crime" to "Crime",
        "categoria/documentario" to "Documentário",
        "categoria/familia" to "Família",
        "categoria/ficcao-cientifica" to "Ficção-Científica",
        "categoria/terror" to "Terror"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}${if (page > 1) "/page/$page/" else "/"}"
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document

        val home = document.select("div.grid a.card").mapNotNull { element ->
            element.toSearchResult()
        }

        val hasNext = document.select("div.pagination a.page-link").any { 
            it.text().contains("Próxima", ignoreCase = true) || 
            it.text().contains("Next", ignoreCase = true) ||
            it.attr("href").contains("page=${page + 1}")
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.card-title")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val posterUrl = this.selectFirst("img.card-img")?.attr("src")?.replace("w342", "original")
        
        val badgeKind = this.selectFirst("span.badge-kind")?.text()?.trim()
        val type = if (badgeKind?.contains("SÉRIE", ignoreCase = true) == true) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }
        
        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "$mainUrl/buscar?q=$encodedQuery"
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        return document.select("div.grid a.card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val title = document.selectFirst("div.title")?.text()?.trim() ?: ""
        val poster = document.selectFirst("img.poster")?.attr("src")?.replace("w500", "original")
        val description = document.selectFirst("div.syn")?.text()?.trim()
        
        val genres = document.select("div.chips a.chip").map { it.text().trim() }
        
        val runtime = document.selectFirst("div.runtime")?.text()?.trim()
        
        val crew = document.selectFirst("div.crew")?.text()?.trim()
        
        val isSeriesPage = document.select("div.tabs button.tab").isNotEmpty()
        
        if (isSeriesPage) {
            val episodes = loadEpisodesFromTabs(document)
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                if (crew != null) addActors(listOf(crew))
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genres
                if (crew != null) addActors(listOf(crew))
                if (runtime != null && runtime.contains("min")) {
                    val durationMatch = Regex("(\\d+)h?\\s?(\\d+)?min").find(runtime)
                    if (durationMatch != null) {
                        val hours = durationMatch.groupValues[1].toIntOrNull() ?: 0
                        val minutes = durationMatch.groupValues[2].toIntOrNull() ?: 0
                        this.duration = (hours * 60) + minutes
                    }
                }
            }
        }
    }

    private fun loadEpisodesFromTabs(document: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        val seasonTabs = document.select("div.tabs button.tab")
        seasonTabs.forEach { tab ->
            val seasonText = tab.text().trim()
            val seasonMatch = Regex("T(\\d+)").find(seasonText)
            val seasonNumber = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            
            val target = tab.attr("data-target")
            val seasonSection = document.selectFirst("div.section#$target")
            
            seasonSection?.let { section ->
                val episodeRows = section.select("table.ep-table tbody tr")
                episodeRows.forEach { row ->
                    val episodeText = row.selectFirst("td.ep-col")?.text()?.trim()
                    val episodeNumber = Regex("Episódio (\\d+)").find(episodeText ?: "")
                        ?.groupValues?.get(1)?.toIntOrNull()
                    
                    val playButton = row.selectFirst("button.btn.bd-play")
                    val episodeUrl = playButton?.attr("data-url")
                    val hasMultipleAudio = playButton?.attr("data-has-dub") == "1" && 
                                         playButton?.attr("data-has-leg") == "1"
                    
                    if (episodeNumber != null && episodeUrl != null) {
                        episodes.add(
                            newEpisode(episodeUrl) {
                                this.name = "Episódio $episodeNumber"
                                this.season = seasonNumber
                                this.episode = episodeNumber
                                if (hasMultipleAudio) {
                                    this.description = "Dublado e Legendado disponível"
                                }
                            }
                        )
                    }
                }
            }
        }
        
        return episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val embedUrl = if (data.startsWith("https://$FEMBED_DOMAIN/e/")) {
                data
            } else {
                val doc = app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).document
                val playerIframe = doc.selectFirst("div.player-wrap iframe#player")?.attr("src")
                playerIframe ?: return false
            }
            
            return extractLinksFromFembed(embedUrl, callback)
        } catch (e: Exception) {
            return false
        }
    }
    
    private suspend fun extractLinksFromFembed(embedUrl: String, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val urlPattern = Regex("https://$FEMBED_DOMAIN/e/([^/]+)/?(.+)?")
            val match = urlPattern.find(embedUrl) ?: return false
            
            val seriesId = match.groupValues[1]
            val episodeInfo = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: ""
            
            val embedDoc = app.get(embedUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
            
            val scripts = embedDoc.select("script")
            var apiParams: Triple<String, String, String>? = null
            
            for (script in scripts) {
                val scriptContent = script.html()
            
                val apiMatch = Regex("const api\\s*=\\s*\"([^\"]+)\"").find(scriptContent)
                if (apiMatch != null) {
                    val apiPath = apiMatch.groupValues[1]
                    val fullApiUrl = "https://$FEMBED_DOMAIN$apiPath"
                    
                    val dataMatch = Regex("const data\\s*=\\s*\\{([^}]+)\\}").find(scriptContent)
                    if (dataMatch != null) {
                        apiParams = Triple(fullApiUrl, "DUB", "MA==")
                        break
                    }
                }
            }
            
            if (apiParams == null) {
                val apiUrl = "https://$FEMBED_DOMAIN/api.php?s=$seriesId&c=$episodeInfo"
                apiParams = Triple(apiUrl, "DUB", "MA==")
            }
            
            val (apiUrl, _, key) = apiParams
            
            val languages = listOf("DUB", "LEG")
            var playerResponse: String? = null
            
            for (lang in languages) {
                val playerHeaders = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "*/*",
                    "Referer" to embedUrl,
                    "Origin" to "https://$FEMBED_DOMAIN",
                    "User-Agent" to USER_AGENT,
                    "Cookie" to API_COOKIE,
                    "Cache-Control" to "no-cache",
                    "Pragma" to "no-cache"
                )
                
                val playerData = mapOf(
                    "action" to "getPlayer",
                    "lang" to lang,
                    "key" to key
                )
                
                try {
                    playerResponse = app.post(apiUrl, headers = playerHeaders, data = playerData).text
                    if (playerResponse.isNotEmpty() && playerResponse.contains("src=")) {
                        break
                    }
                } catch (e: Exception) {
                }
            }
            
            if (playerResponse == null) {
                return false
            }
            
            val iframePattern = Regex("src=\"([^\"]+)\"")
            val iframeMatch = iframePattern.find(playerResponse) ?: return false
            
            val iframeSrc = iframeMatch.groupValues[1]
            val adsUrl = if (iframeSrc.startsWith("/")) "https://$FEMBED_DOMAIN$iframeSrc" else iframeSrc
            
            val adsHeaders = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cache-Control" to "no-cache",
                "Referer" to embedUrl,
                "User-Agent" to USER_AGENT,
                "Cookie" to API_COOKIE,
                "Upgrade-Insecure-Requests" to "1",
                "Sec-Fetch-Dest" to "iframe",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-User" to "?1"
            )
            
            val adsResponse = app.get(adsUrl, headers = adsHeaders).text
            
            val filemoonPattern = Regex("src=\"(https://filemoon\\.(to|in)/e/[^\"]+)\"")
            val filemoonMatch = filemoonPattern.find(adsResponse) ?: return false
            
            val filemoonUrl = filemoonMatch.groupValues[1]
            
            return loadExtractor(filemoonUrl, subtitleCallback = {}, callback = callback)
            
        } catch (e: Exception) {
            return false
        }
    }
}