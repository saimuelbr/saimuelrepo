package com.VisionCine

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.Jsoup

object VisionCineSession {
    private val mainUrl = "https://www.visioncine-1.com.br"
    var manualCookie: String? = "PHPSESSID=96dpur35mt1obi3p5jdaf1hq36"

    private fun buildHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val base = mutableMapOf(
            "User-Agent" to "Mozilla/5.0",
            "X-Requested-With" to "XMLHttpRequest"
        )
        base.putAll(extra)
        manualCookie?.let { base["Cookie"] = it }
        return base
    }

    suspend fun get(url: String, referer: String? = null): NiceResponse {
        val headers = buildHeaders(referer?.let { mapOf("Referer" to it) } ?: emptyMap())
        return app.get(url, headers = headers)
    }

    suspend fun post(url: String, data: Map<String, String>, referer: String? = null): NiceResponse {
        val headers = buildHeaders(referer?.let { mapOf("Referer" to it) } ?: emptyMap())
        return app.post(url, data = data, headers = headers)
    }
}

class VisionCine : MainAPI() {
    override var mainUrl = "https://www.visioncine-1.com.br"
    override var name = "VisionCine"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    


    override val mainPage = mainPageOf(
        "genre/acao" to "Ação",
        "genre/animes" to "Animes",
        "genre/apple%20tv" to "Apple TV",
        "genre/brasileiro" to "Brasileiro",
        "genre/comedia" to "Comédia",
        "genre/crime" to "Crime",
        "genre/dc" to "DC",
        "genre/disney" to "Disney",
        "genre/doramas" to "Doramas",
        "genre/drama" to "Drama",
        "genre/guerra" to "Guerra",
        "genre/marvel" to "Marvel",
        "genre/novelas" to "Novelas",
        "genre/telecine" to "Telecine",
        "genre/netflix" to "Netflix",
        "genre/amazon" to "Amazon Prime",
        "genre/hbo" to "HBO",
        "genre/globoplay" to "Globoplay"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}${if (page > 1) "?page=$page" else ""}"
        val doc = Jsoup.parse(VisionCineSession.get(url).text)
        val items = doc.select("section.listContent .item.poster")
        val list = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, list), hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a.btn.free") ?: selectFirst("a.btn.free.fw-bold")
        val href = a?.attr("href") ?: return null
        val title = selectFirst("h6")?.text() ?: return null
        val imgStyle = selectFirst(".content")?.attr("style")
        val img = imgStyle?.let { Regex("url\\((.*?)\\)").find(it)?.groupValues?.getOrNull(1) }
        val year = selectFirst(".tags span")?.allElements?.mapNotNull { it.text().toIntOrNull() }?.firstOrNull()
        val type = if (selectFirst(".tags span")?.text()?.contains("Temporadas") == true) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = img
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.php?q=$query"
        val doc = Jsoup.parse(VisionCineSession.get(url).text)
        return doc.select("section.listContent .item.poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = Jsoup.parse(VisionCineSession.get(url).text)
        val title = doc.selectFirst("h1.fw-bolder")?.text() ?: ""
        val plot = doc.selectFirst("p.small.linefive")?.text()
        val year = doc.select("p.log span").mapNotNull { it.text().toIntOrNull() }.firstOrNull()
        val posterStyle = doc.selectFirst(".backImage")?.attr("style")
        val poster = posterStyle?.let { Regex("url\\('(.+?)'\\)").find(it)?.groupValues?.getOrNull(1) }
        val isSerie = url.contains("/series") || doc.selectFirst("#seasons-view") != null
        val recommendations = emptyList<SearchResponse>()

        if (isSerie) {
            val seasons = doc.select("#seasons-view option").mapNotNull { it.attr("value").toIntOrNull() }
            val episodes = mutableListOf<Episode>()
            for ((seasonIndex, seasonId) in seasons.withIndex()) {
                val epList = getEpisodesForSeason(seasonId, seasonIndex + 1)
                episodes.addAll(epList)
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            val sources = mutableListOf<String>()
            
            val playBtns = doc.select("a.btn.free.fw-bold, a.btn.free")
            
            playBtns.forEach { btn ->
                val href = btn.attr("href")
                val btnText = btn.text().trim()
                
                if (href.startsWith("http://www.playcinevs.info/") || href.startsWith("https://www.playcinevs.info/")) {
                    sources.add("$btnText|$href")
                }
            }
            
            val dropdownSources = doc.select(".btn-group.dropup.sources-dropdown .dropdown-menu .dropdown-item.source-btn")
            
            dropdownSources.forEach { source ->
                val href = source.attr("href")
                val sourceText = source.text().trim()
                
                if (href.isNotEmpty() && (href.startsWith("http://www.playcinevs.info/") || href.startsWith("https://www.playcinevs.info/"))) {
                    val cleanText = sourceText.replace(Regex("\\s+"), " ").trim()
                    sources.add("$cleanText|$href")
                }
            }
            
            if (sources.isEmpty()) {
                sources.add("Padrão|$url")
            }
            
            return newMovieLoadResponse(title, url, TvType.Movie, sources) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun getEpisodesForSeason(seasonId: Int, seasonNumber: Int): List<Episode> {
        val url = "$mainUrl/ajax/episodes.php?season=$seasonId&page=1"
        val doc = Jsoup.parse(VisionCineSession.get(url, url).text)
        val eps = doc.select("div.ep")
        
        return eps.mapNotNull { ep ->
            val epNum = ep.selectFirst("p[number]")?.text()?.toIntOrNull() ?: 0
            val name = ep.selectFirst("h5.fw-bold")?.text() ?: "Episódio $epNum"
            
            val playBtn = ep.selectFirst("a.btn.free.fw-bold, a.btn.free")
            val episodeUrl = playBtn?.attr("href")
            
            if (episodeUrl.isNullOrEmpty()) {
                return@mapNotNull null
            }
            
            newEpisode(episodeUrl) {
                this.name = name
                this.episode = epNum
                this.season = seasonNumber
                this.data = episodeUrl
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val cleanData = when {
                data.startsWith("[") && data.endsWith("]") -> {
                    data.removePrefix("[").removeSuffix("]").removeSurrounding("\"")
                }
                else -> data
            }
            
            val episodeUrl = if (cleanData.contains("|")) {
                cleanData.split("|").lastOrNull() ?: cleanData
            } else {
                cleanData
            }
            
            val doc = Jsoup.parse(VisionCineSession.get(episodeUrl).text)
            
            val sources = mutableListOf<String>()
            
            val playBtns = doc.select("a.btn.free.fw-bold, a.btn.free")
            
            playBtns.forEach { btn ->
                val href = btn.attr("href")
                val btnText = btn.text().trim()
                
                if (href.startsWith("http://www.playcinevs.info/") || href.startsWith("https://www.playcinevs.info/")) {
                    sources.add("$btnText|$href")
                }
            }
            
            val dropdownSources = doc.select(".btn-group.dropup.sources-dropdown .dropdown-menu .dropdown-item.source-btn")
            
            dropdownSources.forEach { source ->
                val href = source.attr("href")
                val sourceText = source.text().trim()
                
                if (href.isNotEmpty() && (href.startsWith("http://www.playcinevs.info/") || href.startsWith("https://www.playcinevs.info/"))) {
                    val cleanText = sourceText.replace(Regex("\\s+"), " ").trim()
                    sources.add("$cleanText|$href")
                }
            }
            
            if (sources.isEmpty()) {
                return false
            }
            
            var foundAny = false
            
            for ((index, sourceData) in sources.withIndex()) {
                val sourceParts = sourceData.split("|", limit = 2)
                val sourceName = if (sourceParts.size > 1) sourceParts[0] else "Fonte $index"
                val playerUrl = if (sourceParts.size > 1) sourceParts[1] else sourceData
                
                val referer = if (playerUrl.contains("playcinevs.info")) {
                    val originalUrl = episodeUrl.replace("http://www.playcinevs.info/", "").replace("https://www.playcinevs.info/", "")
                    "https://www.visioncine-1.com.br/$originalUrl"
                } else {
                    null
                }
                
                try {
                    val res = Jsoup.parse(VisionCineSession.get(playerUrl, referer).text)
                    
                    val scripts = res.select("script")
                    val inlineScripts = scripts.filter { !it.hasAttr("src") }
                    val scriptWithPlayer = inlineScripts.firstOrNull { it.data().contains("initializePlayer") }
                    
                    val videoUrl = scriptWithPlayer?.let { script ->
                        val scriptContent = script.data()
                        
                        val patterns = listOf(
                            Regex("initializePlayerWithSubtitle\\(['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"],\\s*['\"]([^'\"]*\\.srt[^'\"]*)['\"]"),
                            Regex("initializePlayer\\(['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                            Regex("file:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                            Regex("src:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                            Regex("source:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                            Regex("url:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]")
                        )
                        
                        val subtitlePatterns = listOf(
                            Regex("['\"]([^'\"]*\\.srt[^'\"]*)['\"]"),
                            Regex("subtitle:\\s*['\"]([^'\"]*\\.srt[^'\"]*)['\"]"),
                            Regex("subs:\\s*['\"]([^'\"]*\\.srt[^'\"]*)['\"]")
                        )
                        
                        var foundVideo: String? = null
                        
                        for (pattern in patterns) {
                            val match = pattern.find(scriptContent)
                            if (match != null) {
                                val currentVideoUrl = match.groupValues.getOrNull(1)
                                val subtitleUrl = match.groupValues.getOrNull(2)
                                
                                if (subtitleUrl != null) {
                                    subtitleCallback(SubtitleFile("pt", subtitleUrl))
                                }
                                
                                foundVideo = currentVideoUrl
                                break
                            }
                        }
                        
                        for (pattern in subtitlePatterns) {
                            val match = pattern.find(scriptContent)
                            if (match != null) {
                                val subtitleUrl = match.groupValues.getOrNull(1)
                                if (subtitleUrl != null) {
                                    subtitleCallback(SubtitleFile("pt", subtitleUrl))
                                }
                            }
                        }
                        
                        foundVideo
                    }
                    
                    if (videoUrl != null) {
                        callback(
                            newExtractorLink(
                                "VisionCine - $sourceName",
                                "VisionCine - $sourceName",
                                videoUrl
                            )
                        )
                        foundAny = true
                    }
                    
                } catch (e: Exception) {
                }
            }
            
            return foundAny
            
        } catch (e: Exception) {
            return false
        }
    }
} 