package com.MegaFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.api.Log

const val MAIN_URL = "https://megaflix.lat"
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

class MegaFlix : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "MegaFlix"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/genero/acao" to "Ação",
        "/genero/animacao" to "Animação",
        "/genero/comedia" to "Comédia",
        "/genero/crime" to "Crime",
        "/genero/documentario" to "Documentário",
        "/genero/drama" to "Drama",
        "/genero/familia" to "Família",
        "/genero/fantasia" to "Fantasia",
        "/genero/faroeste" to "Faroeste",
        "/genero/guerra" to "Guerra",
        "/genero/misterio" to "Mistério",
        "/genero/sci-fi" to "Sci-fi",
        "/genero/thiller" to "Thriller"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url = "$mainUrl${request.data}"
        val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val items = doc.select("div.row.row-cols-xxl-6.row-cols-md-4.row-cols-2#content div.col-lg-2")
        
        if (items.isEmpty()) {
            val altItems = doc.select("div.col-lg-2")
            if (altItems.isNotEmpty()) {
                val results = altItems.mapNotNull { item ->
                    toSearchResult(item)
                }
                
                return newHomePageResponse(
                    list = HomePageList(
                        name = request.name,
                        list = results,
                        isHorizontalImages = false
                    ),
                    hasNext = false
                )
            }
        }
        
        val results = items.mapNotNull { item ->
            toSearchResult(item)
        }
        
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = results,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun toSearchResult(item: Element): SearchResponse? {
        val link = item.select("a.card.card-movie").attr("href") ?: return null
        val title = item.select("h3.title").text().trim()
        
        var poster = item.select("picture img").attr("src").takeIf { it.isNotEmpty() }
            ?: item.select("img").attr("src").takeIf { it.isNotEmpty() }
            ?: ""
        
        if (poster.isEmpty()) {
            poster = item.select("picture img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: item.select("img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: ""
        }
        
        if (poster.isEmpty()) {
            val itemHtml = item.html()
            val tmdbMatch = Regex("""https://image\.tmdb\.org/t/p/w500/[^"'\s]+""").find(itemHtml)
            poster = tmdbMatch?.value ?: ""
        }
        
        val year = item.select("li.list-inline-item").firstOrNull()?.text()?.trim()?.toIntOrNull()
        val type = item.select("div.card-type").text().trim()
        
        val tvType = when {
            type.contains("Filme") -> TvType.Movie
            type.contains("Série") || type.contains("Serie") -> TvType.TvSeries
            else -> TvType.Movie
        }
        
        return newMovieSearchResponse(title, link) {
            this.posterUrl = poster
            this.year = year
            this.type = tvType
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/procurar/${query.replace(" ", "%20")}"
        val doc = app.get(searchUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val items = doc.select("div.row.row-cols-xxl-6.row-cols-md-4.row-cols-2#content div.col-lg-2")
        
        return items.mapNotNull { item ->
            toSearchResult(item)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val title = doc.select("h1.h3").text().trim()
        val poster = doc.select("div.w-lg-250 picture img").attr("src").replace("/w300/", "/original/")
        val year = doc.select("ul.list-inline.list-separator li.list-inline-item").firstOrNull()?.text()?.trim()?.toIntOrNull()
        val duration = doc.select("ul.list-inline.list-separator li.list-inline-item").getOrNull(1)?.text()?.trim()
        val plot = doc.select("p.fs-sm.text-muted").text().trim()
        val genre = doc.select("div.card-tag a").text().trim()
        
        val hasSeasons = doc.select("div.card-season").isNotEmpty()
        
        return if (hasSeasons) {
            val episodes = getEpisodesFromSeasons(doc, url)
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = listOf(genre)
                if (duration != null) addDuration(duration)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = listOf(genre)
                if (duration != null) addDuration(duration)
            }
        }
    }

    private suspend fun getEpisodesFromSeasons(doc: org.jsoup.nodes.Document, url: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        val scriptContent = doc.select("script").find { it.data().contains("item_id") }?.data() ?: return emptyList()
        val itemIdMatch = Regex("""let item_id = (\d+);""").find(scriptContent)
        val itemUrlMatch = Regex("""let item_url = '([^']+)';""").find(scriptContent)
        
        if (itemIdMatch != null && itemUrlMatch != null) {
            val itemId = itemIdMatch.groupValues[1]
            val itemUrl = itemUrlMatch.groupValues[1]
            
            val seasonElements = doc.select("div.accordion-item")
            
            for (seasonElement in seasonElements) {
                val seasonNumber = seasonElement.select("div.select-season").attr("data-season").toIntOrNull() ?: continue
                val seasonEpisodes = getEpisodes(itemId, seasonNumber, itemUrl)
                
                seasonEpisodes.forEach { episode ->
                    episode.season = seasonNumber
                }
                
                episodes.addAll(seasonEpisodes)
            }
        }
        
        return episodes
    }

    private suspend fun getEpisodes(itemId: String, season: Int, itemUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        try {
            val response = app.post(
                "$mainUrl/api/seasons",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to mainUrl
                ),
                data = mapOf(
                    "item_id" to itemId,
                    "season" to season.toString(),
                    "item_url" to itemUrl
                )
            )
            
            val episodeDoc = response.document
            val episodeElements = episodeDoc.select("div.card-episode")
            
            for (episodeElement in episodeElements) {
                val episodeLink = episodeElement.select("a.episode").attr("href")
                val episodeNumber = episodeElement.select("a.episode").text().trim()
                val episodeName = episodeElement.select("a.name").text().trim()
                
                val episodeNum = episodeNumber.replace("Episodio ", "").toIntOrNull() ?: continue
                
                episodes.add(
                    Episode(
                        data = episodeLink,
                        episode = episodeNum,
                        name = episodeName
                    )
                )
            }
        } catch (e: Exception) {
            // Error getting episodes
        }
        
        return episodes
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        var playerLinks = mutableListOf<org.jsoup.nodes.Element>()
        
        val primaryPlayers = doc.select("ul.card-episode-nav.players li a")
        if (primaryPlayers.isNotEmpty()) {
            playerLinks.addAll(primaryPlayers)
        }
        
        val altPlayers1 = doc.select("ul.players li a")
        if (altPlayers1.isNotEmpty()) {
            playerLinks.addAll(altPlayers1)
        }
        
        val altPlayers2 = doc.select("div.players a")
        if (altPlayers2.isNotEmpty()) {
            playerLinks.addAll(altPlayers2)
        }
        
        val altPlayers3 = doc.select("a[data-url]")
        if (altPlayers3.isNotEmpty()) {
            playerLinks.addAll(altPlayers3)
        }
        
        val uniquePlayers = playerLinks.distinctBy { it.attr("data-url") }
        var hasValidLinks = false
        
        for (playerLink in uniquePlayers) {
            val playerUrl = playerLink.attr("data-url")
            val playerName = playerLink.text().trim()
            
            if (playerUrl.isEmpty() || playerName.isEmpty()) {
                continue
            }
            
            try {
                val success = when {
                    playerUrl.contains("playhide.shop") -> {
                        extractMegahideLinks(playerUrl, playerName, callback)
                    }
                    playerUrl.contains("filemoon.sx") -> {
                        loadExtractor(playerUrl, playerUrl, subtitleCallback, callback)
                    }
                    playerUrl.contains("listeamed.net") -> {
                        loadExtractor(playerUrl, playerUrl, subtitleCallback, callback)
                    }
                    playerUrl.contains("playerwish.com") -> {
                        loadExtractor(playerUrl, playerUrl, subtitleCallback, callback)
                    }
                    else -> false
                }
                
                if (success) {
                    hasValidLinks = true
                }
                
            } catch (e: Exception) {
                Log.e("MegaFlix", "Error processing player '$playerName': ${e.message}")
            }
        }
        
        return hasValidLinks
    }

    private suspend fun extractMegahideLinks(url: String, playerName: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val response = app.get(
                url,
                headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
            )
            
            val scriptContent = response.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
            
            if (scriptContent != null) {
                val m3u8 = JsUnpacker(scriptContent).unpack()?.let { unpacked ->
                    val match = Regex("""https://[^"'\s]+\.m3u8[^"'\s]*""").find(unpacked)
                    match?.value
                }
                
                if (m3u8 != null && !m3u8.contains("hls4")) {
                    val m3u8Links = M3u8Helper.generateM3u8(
                        playerName,
                        m3u8,
                        mainUrl,
                        headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
                    )
                    
                    m3u8Links.forEach { link ->
                        callback(link)
                    }
                    return true
                }
            }
            
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(https?://[^"'\s]+\.(?:m3u8|mp4|avi|mkv|webm))"""),
                additionalUrls = listOf(Regex("""(https?://[^"'\s]+\.(?:m3u8|mp4|avi|mkv|webm))""")),
                useOkhttp = false,
                timeout = 15_000L
            )
            
            val intercepted = app.get(url, interceptor = resolver).url
            
            if (intercepted.isNotEmpty() && !intercepted.endsWith(".txt") && !intercepted.contains("/e/") && !intercepted.contains("hls4")) {
                if (intercepted.contains(".m3u8")) {
                    val m3u8Links = M3u8Helper.generateM3u8(
                        playerName,
                        intercepted,
                        mainUrl,
                        headers = mapOf("Accept-Language" to "en-US,en;q=0.5")
                    )
                    m3u8Links.forEach { link ->
                        callback(link)
                    }
                } else {
                    callback(newExtractorLink(playerName, playerName, intercepted, INFER_TYPE) {
                        this.referer = mainUrl
                    })
                }
                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            Log.e("MegaFlix", "Error extracting Megahide links: ${e.message}")
            return false
        }
    }
}

class Vidguardto2 : Vidguardto() {
    override var name = "Vidguardto2"
    override var mainUrl = "https://listeamed.net"
}

class Playerwish : StreamWishExtractor() {
    override var name = "Playerwish"
    override var mainUrl = "https://playerwish.com"
}