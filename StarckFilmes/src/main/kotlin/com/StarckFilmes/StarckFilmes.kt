package com.StarckFilmes

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class StarckFilmes : MainAPI() {
    
    companion object {
        private val TRACKER_LIST_URLS = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/XIU2/TrackersListCollection/refs/heads/master/best.txt",
            "https://newtrackon.com/api/all"
        )
    }
    
    override var mainUrl = "https://starckfilmes-v8.com"
    override var name = "StarckFilmes"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)

    override val mainPage = mainPageOf(
        "?genre=ação" to "Ação",
        "?genre=animação" to "Animação",
        "?genre=aventura" to "Aventura",
        "?genre=comédia" to "Comédia",
        "?genre=crime" to "Crime",
        "?genre=documentário" to "Documentários",
        "?genre=drama" to "Drama",
        "?genre=família" to "Família",
        "?genre=ficção" to "Ficção",
        "?genre=ficção-científica" to "Ficção-Científica",
        "?genre=guerra" to "Guerra",
        "?genre=mistério" to "Mistério",
        "?genre=romance" to "Romance",
        "?genre=suspense" to "Suspense",
        "?genre=terror" to "Terror"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document
        val home = document.select("div.home.post-catalog div.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("a.title").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("div.post-image-sub").attr("data-bk")
        
        val isSeries = title.contains("S0", ignoreCase = true) || 
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true)
        
        return if (isSeries) {
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
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.home.post-catalog div.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.post-description h2.post-title")?.text()?.trim() ?: ""
        
        var poster = document.selectFirst("div.post-image img")?.attr("src")
        
        if (poster.isNullOrBlank()) {
            val trailerDiv = document.selectFirst("div.trailer")
            val youtubeLink = trailerDiv?.attr("data-youtube-link")
            
            if (!youtubeLink.isNullOrBlank()) {
                val youtubeIdMatch = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]+)""").find(youtubeLink)
                if (youtubeIdMatch != null) {
                    val videoId = youtubeIdMatch.groupValues[1]
                    poster = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                }
            }
        }
        
        val description = document.selectFirst("div.sinopse span:last-child")?.text()?.trim()
        
        val yearElement = document.select("div.post-description p").find { element ->
            element.text().contains("Lançamento:", ignoreCase = true)
        }
        val yearText = yearElement?.select("span:last-child")?.text()?.trim()
        val year = yearText?.toIntOrNull()
        
        val genreElement = document.select("div.post-description p").find { element ->
            element.text().contains("Gênero:", ignoreCase = true)
        }
        
        val genresText = genreElement?.select("span:last-child")?.text()?.trim()
        val genres = genresText?.split(", ")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        
        val quality = document.selectFirst("div.post-description div.meta span.sl-quality")?.text()?.trim()
        if (!quality.isNullOrBlank()) {
            genres.add(0, "Qualidade: $quality")
        }
        
        val durationElement = document.select("div.post-description div.meta span").find { element ->
            element.text().contains("min", ignoreCase = true)
        }
        val duration = durationElement?.text()?.trim()
        if (!duration.isNullOrBlank()) {
            genres.add(0, "Duração: $duration")
        }
        
        val isSeries = document.select("div.post-buttons div.epsodios").isNotEmpty() ||
                      title.contains("S0", ignoreCase = true) ||
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true)
        
        return if (isSeries) {
            val episodes = loadEpisodesFromPage(document, url)
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val isEpisode = data.contains("|")
        val url: String
        val episodeNumber: Int?
        
        if (isEpisode) {
            val parts = data.split("|")
            url = parts[0]
            episodeNumber = parts[1].toIntOrNull()
        } else {
            url = data
            episodeNumber = null
        }
        
        val document = app.get(url).document
        
        return if (isEpisode && episodeNumber != null) {
            val episodeResult = loadEpisodeLinks(document, episodeNumber, subtitleCallback, callback)
            if (!episodeResult) {
                val singleSeasonMagnet = document.select("div.post-buttons div.buttons-content a[href^='magnet:']")
                val episodiosDiv = document.select("div.post-buttons div.epsodios")
                
                if (singleSeasonMagnet.isNotEmpty() && episodiosDiv.isEmpty()) {
                    return false
                }
            }
            episodeResult
        } else {
            loadMovieLinks(document, subtitleCallback, callback)
        }
    }
    
    private suspend fun loadEpisodeLinks(
        document: org.jsoup.nodes.Document,
        episodeNumber: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodiosDiv = document.select("div.post-buttons div.epsodios")
        if (episodiosDiv.isEmpty()) {
            return false
        }
        
        val allParagraphs = episodiosDiv.select("p")
        
        val episodeParagraph = allParagraphs.find { paragraph ->
            val text = paragraph.text().trim()
            
            val episodeMatch = Regex("EPISÓDIOS\\s*0?$episodeNumber:", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPISÓDIO\\s*0?$episodeNumber:", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPIS.*DIOS\\s*0?$episodeNumber", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPIS.*DIO\\s*0?$episodeNumber", RegexOption.IGNORE_CASE).find(text)
            
            episodeMatch != null
        }
        
        if (episodeParagraph == null) {
            return false
        }
        
        val magnetLinks = episodeParagraph.select("a[href^='magnet:']")
        
        if (magnetLinks.isEmpty()) {
            return false
        }

        for (link in magnetLinks) {
            val originalMagnetUrl = link.attr("href")
            val enhancedMagnetUrl = enhanceMagnetWithTrackers(originalMagnetUrl)
            val quality = link.text().trim()
            
            val magnetInfo = extractMagnetInfo(enhancedMagnetUrl)
            
            val sourceName = buildString {
                append("StarckFilmes Ep$episodeNumber")
                append(" $quality")
                if (magnetInfo.contains("DUAL", ignoreCase = true)) append(" Dual Áudio")
                if (magnetInfo.contains("MKV", ignoreCase = true)) append(" MKV")
                if (magnetInfo.contains("MP4", ignoreCase = true)) append(" MP4")
            }
            
            callback(
                newExtractorLink(
                    sourceName,
                    sourceName,
                    enhancedMagnetUrl,
                    ExtractorLinkType.MAGNET
                ) {
                    this.referer = mainUrl
                }
            )
        }
        
        val subtitleLink = document.selectFirst("a[href*='opensubtitles.org']")
        if (subtitleLink != null) {
            val subtitleUrl = subtitleLink.attr("href")
            
            try {
                subtitleCallback(SubtitleFile("pt", subtitleUrl))
            } catch (e: Exception) {
            }
        }
        
        return true
    }
    
    private suspend fun loadMovieLinks(
        document: org.jsoup.nodes.Document,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val magnetLinks = document.select("a[href^='magnet:']")
        
        if (magnetLinks.isEmpty()) {
            return false
        }

        for (link in magnetLinks) {
            val originalMagnetUrl = link.attr("href")
            val enhancedMagnetUrl = enhanceMagnetWithTrackers(originalMagnetUrl)
            
            val buttonContainer = link.closest("span.btn-down")
            val textSpans = buttonContainer?.select("span.text span")
            
            var audioType = ""
            var format = ""
            var quality = ""
            var size = ""
            
            textSpans?.forEach { span ->
                val text = span.text().trim()
                
                when {
                    text.contains("Dual Áudio", ignoreCase = true) -> audioType = "Dual Áudio"
                    text.contains("Legendado", ignoreCase = true) -> audioType = "Legendado"
                    text.contains("Dublado", ignoreCase = true) -> audioType = "Dublado"
                    text.contains("MKV", ignoreCase = true) -> format = "MKV"
                    text.contains("MP4", ignoreCase = true) -> format = "MP4"
                    text.contains("AVI", ignoreCase = true) -> format = "AVI"
                    text.contains("1080p", ignoreCase = true) -> quality = "1080p"
                    text.contains("720p", ignoreCase = true) -> quality = "720p"
                    text.contains("480p", ignoreCase = true) -> quality = "480p"
                    text.matches(Regex(".*\\(.*GB.*\\).*")) -> size = text
                }
            }
            
            val sourceName = buildString {
                append("StarckFilmes")
                if (audioType.isNotEmpty()) append(" $audioType")
                if (quality.isNotEmpty()) append(" $quality")
                if (format.isNotEmpty()) append(" $format")
                if (size.isNotEmpty()) append(" ($size)")
            }
            
            val magnetInfo = extractMagnetInfo(enhancedMagnetUrl)
            
            val linkType = ExtractorLinkType.MAGNET
            
            callback(
                newExtractorLink(
                    sourceName,
                    sourceName,
                    enhancedMagnetUrl,
                    linkType
                ) {
                    this.referer = mainUrl
                }
            )
        }
        
        val subtitleLink = document.selectFirst("a[href*='opensubtitles.org']")
        if (subtitleLink != null) {
            val subtitleUrl = subtitleLink.attr("href")
            
            try {
                subtitleCallback(SubtitleFile("pt", subtitleUrl))
            } catch (e: Exception) {
            }
        }
        
        return true
    }
    
    private fun loadEpisodesFromPage(document: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        val episodiosDiv = document.select("div.post-buttons div.epsodios")
        if (episodiosDiv.isEmpty()) {
            return episodes
        }
        
        val episodeParagraphs = episodiosDiv.select("p")
        
        episodeParagraphs.forEach { paragraph ->
            val paragraphText = paragraph.text().trim()
            
            val episodeMatches = mutableListOf<Int>()
            
            val multipleMatch = Regex("EPISÓDIOS\\s*(\\d+)\\s*E\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
            if (multipleMatch != null) {
                val ep1 = multipleMatch.groupValues[1].toIntOrNull()
                val ep2 = multipleMatch.groupValues[2].toIntOrNull()
                if (ep1 != null) episodeMatches.add(ep1)
                if (ep2 != null) episodeMatches.add(ep2)
            } else {
                val singleMatch = Regex("EPISÓDIOS\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
                if (singleMatch != null) {
                    val episodeNumber = singleMatch.groupValues[1].toIntOrNull()
                    if (episodeNumber != null) episodeMatches.add(episodeNumber)
                } else {
                    val altMatch = Regex("EPISÓDIO\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
                    if (altMatch != null) {
                        val episodeNumber = altMatch.groupValues[1].toIntOrNull()
                        if (episodeNumber != null) episodeMatches.add(episodeNumber)
                    }
                }
            }
            
            episodeMatches.forEach { episodeNumber ->
                val episodeMagnetLinks = paragraph.select("a[href^='magnet:']")
                
                val episode = newEpisode("$baseUrl|$episodeNumber") {
                    this.name = "Episódio $episodeNumber"
                    this.episode = episodeNumber
                    this.season = 1
                }
                
                episodes.add(episode)
            }
        }
        
        return episodes
    }
    
    private suspend fun getUpdatedTrackers(): List<String> {
        val allTrackers = mutableSetOf<String>()
        
        for (url in TRACKER_LIST_URLS) {
            try {
                val response = app.get(url).text
                val trackers = response.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && (it.startsWith("http") || it.startsWith("udp") || it.startsWith("wss") || it.startsWith("ws")) }
                    .filter { it.contains("/announce") || it.contains(":443") || it.contains(":80") || it.contains(":6969") || it.contains(":1337") }
                
                allTrackers.addAll(trackers)
            } catch (e: Exception) {
            }
        }
        
        return allTrackers.toList()
    }

    private suspend fun enhanceMagnetWithTrackers(magnetUrl: String): String {
        val premiumTrackers = getUpdatedTrackers()
        
        try {
            val decodedUrl = java.net.URLDecoder.decode(magnetUrl, "UTF-8")
            
            val existingTrackers = mutableSetOf<String>()
            val trackerMatches = Regex("&tr=([^&]+)").findAll(decodedUrl)
            
            trackerMatches.forEach { match ->
                val tracker = java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
                existingTrackers.add(tracker)
            }
            
            val newTrackers = premiumTrackers.filter { tracker ->
                val trackerDomain = tracker.substringAfter("://").substringBefore("/")
                !existingTrackers.any { existing ->
                    existing.contains(trackerDomain)
                }
            }
            
            if (newTrackers.isNotEmpty()) {
                val enhancedUrl = buildString {
                    append(decodedUrl)
                    newTrackers.forEach { tracker ->
                        append("&tr=")
                        append(java.net.URLEncoder.encode(tracker, "UTF-8"))
                    }
                }
                Log.d("StarckFilmes", "Enhanced magnet with ${newTrackers.size} new trackers (total: ${premiumTrackers.size})")
                return enhancedUrl
            }
            
            Log.d("StarckFilmes", "No new trackers to add (total available: ${premiumTrackers.size})")
            return magnetUrl
        } catch (e: Exception) {
            Log.e("StarckFilmes", "Error enhancing magnet: ${e.message}")
            return magnetUrl
        }
    }
    
    private fun extractMagnetInfo(magnetUrl: String): String {
        return try {
            val decodedUrl = java.net.URLDecoder.decode(magnetUrl, "UTF-8")
            
            val nameMatch = Regex("&dn=([^&]+)").find(decodedUrl)
            val fileName = nameMatch?.groupValues?.get(1) ?: ""
            
            val extensionMatch = Regex("\\.([a-zA-Z0-9]+)(?:[?&]|$)").find(fileName)
            val extension = extensionMatch?.groupValues?.get(1) ?: ""
            
            "$fileName ($extension)"
        } catch (e: Exception) {
            "desconhecido"
        }
    }
} 