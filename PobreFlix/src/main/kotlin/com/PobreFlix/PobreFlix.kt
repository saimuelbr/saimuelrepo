package com.PobreFlix


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.FileMoonSx
import org.jsoup.nodes.Element
import java.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

const val MAIN_URL = "https://pobreflixtv.asia"
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

class PobreFlix : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "PobreFlix"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/genero/filmes-de-animacao-1/?sortby=v_rating&sortdirection=desc" to "Filmes - Animação",
        "/genero/filmes-de-ficcao-cientifica-11/?sortby=v_rating&sortdirection=desc" to "Filmes - Ficção Cientifica",
        "/genero/filmes-de-documentario-6/?sortby=v_last_comment&sortdirection=desc" to "Filmes - Documentario",
        "/genero/filmes-de-suspense-18/?sortby=v_rating&sortdirection=desc" to "Filmes - Suspense",
        "/genero/filmes-de-comedia-4/?sortby=v_rating&sortdirection=desc" to "Filmes - Comédia",
        "/genero/filmes-de-guerra-12/?sortby=v_rating&sortdirection=desc" to "Filmes - Guerra",
        "/genero/filmes-de-misterio-14/?sortby=v_rating&sortdirection=desc" to "Filmes - Mistério",
        "/genero/series-de-animacao-20/?sortby=v_rating&sortdirection=desc" to "Séries - Animação",
        "/genero/series-de-ficcao-cientifica-30/?sortby=v_rating&sortdirection=desc" to "Séries - Ficção Cientifica",
        "/genero/series-de-documentario-25/?sortby=v_rating&sortdirection=desc" to "Séries - Documentario",
        "/genero/series-de-comedia-23/?sortby=v_started&sortdirection=desc" to "Séries - Comédia",
        "/genero/series-de-crime-24/?sortby=v_rating&sortdirection=desc" to "Séries - Crime",
        "/genero/series-de-guerra-31/?sortby=v_rating&sortdirection=desc" to "Séries - Guerra",
        "/genero/series-de-misterio-32/?sortby=v_rating&sortdirection=desc" to "Séries - Mistério"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}?page=$page"
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(url, headers = headers).document
        
        val home = document.select("div#collview div.vbItemImage").filter { el ->
            el.selectFirst("span.capa-info.capa-audio")?.text()?.contains("LEG", ignoreCase = true) == false
        }.mapNotNull { it.toSearchResult() }

        val hasNext = document.select("li.ipsPagination_next:not(.ipsPagination_inactive)").isNotEmpty()

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
        val link = selectFirst("a")?.attr("href") ?: return null
        val title = selectFirst("div.caption a")?.text()?.trim() ?: return null
        val poster = selectFirst("div.vb_image_container")?.attr("data-background-src")
        val year = selectFirst("span.y")?.text()?.toIntOrNull()
        
        return newMovieSearchResponse(title, fixUrl(link), TvType.Movie) {
            this.posterUrl = poster?.let { fixUrl(it) }
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/pesquisar/?videobox=&p=$query"
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(url, headers = headers).document
        
        return document.select("div#collview div.vbItemImage").filter { el ->
            el.selectFirst("span.capa-info.capa-audio")?.text()?.contains("LEG", ignoreCase = true) == false
        }.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val title = document.selectFirst("h1.ipsType_pageTitle span.titulo")?.ownText()?.trim() ?: "Sem título"
        
        var poster: String? = null
        var videoEmbedDiv = document.selectFirst("div#video_embed")
        
        if (videoEmbedDiv != null) {
            val style = videoEmbedDiv.attr("style")
            val backgroundImageMatch = Regex("""background-image:url\(([^)]+)\)""").find(style)
            if (backgroundImageMatch != null) {
                poster = backgroundImageMatch.groupValues[1].let { fixUrl(it) }
            }
        } else {
            val onlineUrl = if (url.contains("?area=online")) url else "$url?area=online"
            try {
                val onlineDoc = app.get(onlineUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                videoEmbedDiv = onlineDoc.selectFirst("div#video_embed")
                if (videoEmbedDiv != null) {
                    val style = videoEmbedDiv.attr("style")
                    val backgroundImageMatch = Regex("""background-image:url\(([^)]+)\)""").find(style)
                    if (backgroundImageMatch != null) {
                        poster = backgroundImageMatch.groupValues[1].let { fixUrl(it) }
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        if (poster == null) {
            poster = document.selectFirst("div.vb_image_container")?.attr("data-background-src")?.let { fixUrl(it) }
        }
        
        val year = document.select("div.infos span").getOrNull(1)?.text()?.toIntOrNull()
        
        val sinopseDiv = document.selectFirst("div.sinopse")
        val plot = if (sinopseDiv != null) {
            val fullText = sinopseDiv.text().trim()
            val lerMaisIndex = fullText.indexOf("Ler mais...")
            if (lerMaisIndex != -1) {
                val textBeforeLerMais = fullText.substring(0, lerMaisIndex)
                val lastDotIndex = textBeforeLerMais.lastIndexOf(".")
                if (lastDotIndex != -1) {
                    textBeforeLerMais.substring(0, lastDotIndex + 1).trim()
                } else {
                    textBeforeLerMais.trim()
                }
            } else {
                fullText
            }
        } else null
        
        val genres = document.select("span.gen a.generos").map { it.text().trim() }.toMutableList()
        val quality = document.selectFirst("div.infos span.box")?.text()?.trim()
        if (!quality.isNullOrBlank()) {
            genres.add(0, "Qualidade: $quality")
        }
        
        val duration = document.select("div.infos span").getOrNull(2)?.text()?.trim()
        if (!duration.isNullOrBlank()) {
            genres.add(0, "Duração: $duration")
        }
        
        val recommendations = document.select("div#vbTabSlider div#collview").mapNotNull { it.toSearchResult() }
        
        val hasEpisodes = document.select("div.listagem ul#listagem li[data-id]").isNotEmpty()
        val hasSeasonSelector = document.select("span.escolha_span").isNotEmpty()
        val isSerie = hasEpisodes || hasSeasonSelector
        
        if (isSerie) {
            val episodes = getEpisodes(document, url)
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.recommendations = recommendations
            }
        } else {
            val sources = getMovieSources(document, url)
            return newMovieLoadResponse(title, url, TvType.Movie, sources) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun getEpisodes(document: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        for (season in 1..21) {
            try {
                val seasonUrl = if (season == 1) baseUrl else "$baseUrl?temporada=$season"
                
                val seasonDoc = if (season == 1) document else app.get(seasonUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                val seasonEpisodes = extractEpisodesFromPage(seasonDoc, season)
                
                if (seasonEpisodes.isNotEmpty()) {
                    episodes.addAll(seasonEpisodes)
                } else {
                    if (season > 3) {
                        val hasNextSeasons = (season + 1..season + 3).any { nextSeason ->
                            try {
                                val nextUrl = "$baseUrl?temporada=$nextSeason"
                                val nextDoc = app.get(nextUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                                extractEpisodesFromPage(nextDoc, nextSeason).isNotEmpty()
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (!hasNextSeasons) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (season > 3) {
                    break
                }
            }
        }
        
        return episodes
    }



    private fun extractEpisodesFromPage(document: org.jsoup.nodes.Document, currentSeason: Int = 1): List<Episode> {
        val episodeElements = document.select("div.listagem ul#listagem li[data-id]")
        
        return episodeElements.mapNotNull { episodeElement ->
            val episodeLink = episodeElement.selectFirst("a")?.attr("href")
            val episodeTitle = episodeElement.selectFirst("a")?.text()?.trim()
            val dataId = episodeElement.attr("data-id")
            
            if (episodeLink != null && episodeTitle != null) {
                val seasonEpisodeRegex = Regex("""(\\d+)x(\\d+)""")
                val match = seasonEpisodeRegex.find(episodeLink)
                
                if (match != null) {
                    val season = match.groupValues[1].toIntOrNull() ?: currentSeason
                    val episode = match.groupValues[2].toIntOrNull() ?: 1
                    
                    newEpisode(fixUrl(episodeLink)) {
                        this.name = episodeTitle
                        this.season = season
                        this.episode = episode
                    }
                } else {
                    val episodeNumber = episodeTitle.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
                    
                    newEpisode(fixUrl(episodeLink)) {
                        this.name = episodeTitle
                        this.season = currentSeason
                        this.episode = episodeNumber
                    }
                }
            } else {
                null
            }
        }
    }

    private fun getMovieSources(document: org.jsoup.nodes.Document, url: String): List<String> {
        val playerUrl = "$url?area=online"
        return listOf(playerUrl)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val realData = when {
            data.startsWith("[") && data.endsWith("]") -> {
                data.removePrefix("[").removeSuffix("]").split(",").first().trim().removeSurrounding("\"")
            }
            else -> data
        }
        val urlAreaOnline = if (realData.contains("?area=online")) realData else "$realData?area=online"
        val playerDoc = app.get(urlAreaOnline, headers = mapOf("User-Agent" to USER_AGENT)).document

        val allPlayerLinks = playerDoc.select("ul#baixar_menu li.ipsMenu_item a")

        val streamtapeLink = allPlayerLinks.firstOrNull { link ->
            val playerName = link.select("b").text().trim().lowercase()
            playerName == "streamtape"
        }?.attr("href")
        if (streamtapeLink != null) {
            val realUrl = extractUrlFromEtvEmbed(streamtapeLink, "streamtape")
            if (realUrl != null) {
                val embedUrl = realUrl.replace(Regex("streamtape\\.com/v/"), "streamtape.com/e/")
                try {
                    val links = com.lagradost.cloudstream3.extractors.StreamTape().getUrl(embedUrl, mainUrl)
                    if (links != null && links.isNotEmpty()) {
                        links.forEach { callback(it) }
                    }
                } catch (e: Exception) {
                }
            }
        }

        val mixdropLink = allPlayerLinks.firstOrNull { link ->
            val playerName = link.select("b").text().trim().lowercase()
            playerName == "mixdrop"
        }?.attr("href")
        if (mixdropLink != null) {
            val realUrl = extractUrlFromEtvEmbed(mixdropLink, "mixdrop")
            if (realUrl != null) {
                val embedUrl = realUrl.replace(Regex("mixdrop\\.(my|to|co|sb|bz|ag)/f/"), "mixdrop.my/e/")
                try {
                    val links = com.lagradost.cloudstream3.extractors.MixDrop().getUrl(embedUrl, mainUrl)
                    if (links != null && links.isNotEmpty()) {
                        links.forEach { callback(it) }
                    }
                } catch (e: Exception) {
                }
            }
        }
        val filemoonLink = allPlayerLinks.firstOrNull { link ->
            val playerName = link.select("b").text().trim().lowercase()
            playerName == "filemoon"
        }?.attr("href")
        if (filemoonLink != null) {
            val realUrl = extractUrlFromEtvEmbed(filemoonLink, "filemoon")
            if (realUrl != null) {
                val filemoonRegex = Regex("https?://([a-zA-Z0-9.]+)/[de]/([a-zA-Z0-9]+)")
                val match = filemoonRegex.find(realUrl)
                val embedUrl = if (match != null) {
                    val domain = match.groupValues[1]
                    val id = match.groupValues[2]
                    "https://$domain/e/$id"
                } else {
                    realUrl
                }
                try {
                    val links = when {
                        embedUrl.contains("filemoon.sx") -> com.lagradost.cloudstream3.extractors.FileMoonSx().getUrl(embedUrl, mainUrl)
                        else -> {
                            val linksTo = com.lagradost.cloudstream3.extractors.FileMoon().getUrl(embedUrl, mainUrl)
                            if (linksTo != null && linksTo.isNotEmpty()) {
                                linksTo
                            } else {
                                com.lagradost.cloudstream3.extractors.FileMoonSx().getUrl(embedUrl, mainUrl)
                            }
                        }
                    }
                    if (links != null && links.isNotEmpty()) {
                        links.forEach { callback(it) }
                    }
                } catch (e: Exception) {
                }
            }
        }
        return false
    }

    private suspend fun getFinalPlayerUrl(redirectUrl: String, playerName: String): String? {
        try {
            val realPlayerUrl = extractUrlFromEtvEmbed(redirectUrl, playerName)
            if (realPlayerUrl != null) {
                val finalUrl = extractIframeFromPlayerPage(realPlayerUrl, playerName)
                if (finalUrl != null) {
                    return finalUrl
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractUrlFromEtvEmbed(etvUrl: String, playerName: String): String? {
        try {
            val urlMatch = Regex("""url=([^&]+)""").find(etvUrl)
            if (urlMatch != null) {
                val encodedUrl = urlMatch.groupValues[1]
                try {
                    val decodedBytes = android.util.Base64.decode(encodedUrl, android.util.Base64.DEFAULT)
                    val decodedUrl = String(decodedBytes)
                    val correctedUrl = decodedUrl.reversed()
                    if (isValidPlayerUrl(correctedUrl, playerName)) {
                        return correctedUrl
                    }
                } catch (e: Exception) {
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun isValidPlayerUrl(url: String, playerName: String): Boolean {
        return when (playerName.lowercase()) {
            "mixdrop" -> url.contains("mixdrop.my") || url.contains("mxdrop.to") || 
                        url.contains("mixdrop.sb") || url.contains("mixdrop.co") ||
                        url.contains("mixdrop.bz") || url.contains("mixdrop.ag")
            "streamtape" -> url.contains("streamtape.com") || url.contains("streamtape.net") ||
                           url.contains("streamtape.to")
            "filemoon" -> url.contains("filemoon.sx")
            "doodstream" -> url.contains("vide0.net") || url.contains("do7go.com") || Regex("https?://[a-zA-Z0-9.]+/d/").containsMatchIn(url)
            else -> false
        }
    }

    private suspend fun extractIframeFromPlayerPage(playerUrl: String, playerName: String): String? {
        try {
            val headers = mapOf("User-Agent" to USER_AGENT)
            val document = app.get(playerUrl, headers = headers).document
            val iframeUrl = when (playerName.lowercase()) {
                "mixdrop" -> {
                    val textarea = document.selectFirst("div.download-embed textarea")
                    if (textarea != null) {
                        val iframeContent = textarea.text()
                        val iframeMatch = Regex("""src=\"([^\"]+)\"""").find(iframeContent)
                        if (iframeMatch != null) {
                            val url = iframeMatch.groupValues[1]
                            url
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                "streamtape" -> {
                    val textarea = document.selectFirst("div.embed textarea")
                    if (textarea != null) {
                        val iframeContent = textarea.text()
                        val iframeMatch = Regex("""src=\"([^\"]+)\"""").find(iframeContent)
                        if (iframeMatch != null) {
                            val url = iframeMatch.groupValues[1]
                            url
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                "doodstream" -> {
                    val textarea = document.selectFirst("div.download-embed textarea")
                    if (textarea != null) {
                        val iframeContent = textarea.text()
                        val iframeMatch = Regex("""src=\"([^\"]+)\"""").find(iframeContent)
                        if (iframeMatch != null) {
                            val url = iframeMatch.groupValues[1]
                            url
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                else -> {
                    null
                }
            }
            return iframeUrl
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractRealPlayerUrlFromEtv(document: org.jsoup.nodes.Document, playerName: String): String? {
        try {
            val scripts = document.select("script")
            for (script in scripts) {
                val scriptContent = script.html()
                if (playerName.lowercase().contains("mixdrop")) {
                    val mixdropMatch = Regex("""['\"](https?://[^'\"]*(?:mixdrop\\.my|mxdrop\\.to)[^'\"]*)['\"]""").find(scriptContent)
                    if (mixdropMatch != null) {
                        val url = mixdropMatch.groupValues[1]
                        return url
                    }
                    val mixdropPatterns = listOf(
                        Regex("""window\\.location\\.href\\s*=\\s*['\"]([^'\"]*(?:mixdrop\\.my|mxdrop\\.to)[^'\"]*)['\"]"""),
                        Regex("""location\\.href\\s*=\\s*['\"]([^'\"]*(?:mixdrop\\.my|mxdrop\\.to)[^'\"]*)['\"]"""),
                        Regex("""redirect\\s*\\(\\s*['\"]([^'\"]*(?:mixdrop\\.my|mxdrop\\.to)[^'\"]*)['\"]""")
                    )
                    for (pattern in mixdropPatterns) {
                        val match = pattern.find(scriptContent)
                        if (match != null) {
                            val url = match.groupValues[1]
                            return url
                        }
                    }
                }
                if (playerName.lowercase().contains("streamtape")) {
                    val streamtapeMatch = Regex("""['\"](https?://[^'\"]*streamtape\\.com[^'\"]*)['\"]""").find(scriptContent)
                    if (streamtapeMatch != null) {
                        val url = streamtapeMatch.groupValues[1]
                        return url
                    }
                    val streamtapePatterns = listOf(
                        Regex("""window\\.location\\.href\\s*=\\s*['\"]([^'\"]*streamtape\\.com[^'\"]*)['\"]"""),
                        Regex("""location\\.href\\s*=\\s*['\"]([^'\"]*streamtape\\.com[^'\"]*)['\"]"""),
                        Regex("""redirect\\s*\\(\\s*['\"]([^'\"]*streamtape\\.com[^'\"]*)['\"]""")
                    )
                    for (pattern in streamtapePatterns) {
                        val match = pattern.find(scriptContent)
                        if (match != null) {
                            val url = match.groupValues[1]
                            return url
                        }
                    }
                }
                if (playerName.lowercase().contains("doodstream")) {
                    val doodstreamMatch = Regex("""['\"](https?://[^'\"]*vide0\\.net[^'\"]*)['\"]""").find(scriptContent)
                    if (doodstreamMatch != null) {
                        val url = doodstreamMatch.groupValues[1]
                        return url
                    }
                    val doodstreamPatterns = listOf(
                        Regex("""window\\.location\\.href\\s*=\\s*['\"]([^'\"]*vide0\\.net[^'\"]*)['\"]"""),
                        Regex("""location\\.href\\s*=\\s*['\"]([^'\"]*vide0\\.net[^'\"]*)['\"]"""),
                        Regex("""redirect\\s*\\(\\s*['\"]([^'\"]*vide0\\.net[^'\"]*)['\"]""")
                    )
                    for (pattern in doodstreamPatterns) {
                        val match = pattern.find(scriptContent)
                        if (match != null) {
                            val url = match.groupValues[1]
                            return url
                        }
                    }
                }
            }
            val metaRefresh = document.selectFirst("meta[http-equiv=refresh]")
            if (metaRefresh != null) {
                val content = metaRefresh.attr("content")
                val urlMatch = Regex("""url=([^;]+)""").find(content)
                if (urlMatch != null) {
                    val url = urlMatch.groupValues[1]
                    return url
                }
            }
            val iframes = document.select("iframe")
            for ((index, iframe) in iframes.withIndex()) {
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    return src
                }
            }
            val links = document.select("a[href]")
            for (link in links) {
                val href = link.attr("href")
                if (href.contains("mixdrop") || href.contains("streamtape") || href.contains("doodstream")) {
                    return href
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    class StreamtapeExtractor : com.lagradost.cloudstream3.utils.ExtractorApi() {
        override val name = "Streamtape"
        override val mainUrl = "https://streamtape.com"
        override val requiresReferer = true

        override suspend fun getUrl(url: String, referer: String?): List<com.lagradost.cloudstream3.utils.ExtractorLink>? {
            val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
            val videoSrc = document.selectFirst("video#mainvideo")?.attr("src")
            if (!videoSrc.isNullOrBlank()) {
                val finalUrl = if (videoSrc.startsWith("//")) "https:$videoSrc" else videoSrc
                return listOf(
                    newExtractorLink(
                        name,
                        "Streamtape Video",
                        finalUrl,
                        INFER_TYPE
                    ) {
                        this.referer = referer ?: mainUrl
                    }
                )
            }
            val scripts = document.select("script")
            for (script in scripts) {
                val scriptContent = script.html()
                val match = Regex("""['\"](https?://[^'\"]*\\.(?:mp4|m3u8|mkv)[^'\"]*)['\"]""").find(scriptContent)
                if (match != null) {
                    val videoUrl = match.groupValues[1]
                    return listOf(
                        newExtractorLink(
                            name,
                            "Streamtape Video",
                            videoUrl,
                            INFER_TYPE
                        ) {
                            this.referer = referer ?: mainUrl
                        }
                    )
                }
            }
            return null
        }
    }
}