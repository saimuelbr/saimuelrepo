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
import org.jsoup.nodes.Element
import okhttp3.HttpUrl

const val MAIN_URL = "https://www.visioncine-1.com.br"

class VisionCine : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "VisionCine"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/genre/acao" to "Ação",
        "/genre/suspense" to "Suspense",
        "/genre/comedia" to "Comédia",
        "/genre/crime" to "Crime",
        "/genre/brasileiro" to "Brasileiro",
        "/genre/guerra" to "Guerra",
        "/genre/documentario" to "Documentário",
        "/animes" to "Animes",
        "/genre/netflix" to "Netflix",
        "/genre/amazon" to "Amazon Prime",
        "/genre/globoplay" to "GloboPlay",
        "/genre/disney" to "Disney",
        "/genre/telecine" to "TeleCine",
        "/genre/hbo" to "HBO",
        "/genre/marvel" to "Marvel",
        "/genre/dc" to "DC",
        "/genre/novelas" to "Novelas"
    )

    private val baseUrl: HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("visioncine.live")
        .build()

    private suspend fun ensureLoggedIn() {
        if (!VisionCineSession.isLoggedIn(baseUrl)) {
            VisionCineSession.clearCookies()
            throw Exception("Sessão expirada. Faça login novamente nas configurações do plugin.")
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        ensureLoggedIn()
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}?page=$page"
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(url, headers = headers).document
        
        val home = document.select("div.items.break div.item.poster").mapNotNull { it.toSearchResult() }
        val hasNext = document.select("a[rel=next]").isNotEmpty()

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
        val link = selectFirst("div.buttons a")?.attr("href") ?: return null
        val title = selectFirst("div.info h6")?.text()?.trim() ?: return null
        val poster = selectFirst("div.content")?.attr("style")?.let { style ->
            Regex("""background-image: url\(([^)]+)\)""").find(style)?.groupValues?.get(1)
        }
        
        val tags = select("div.info p.tags span").map { it.text().trim() }
        val year = tags.find { it.matches(Regex("\\d{4}")) }?.toIntOrNull()
        val duration = tags.find { it.contains("Min") || it.contains("Temporadas") }?.trim()
        
        val isSerie = duration?.contains("Temporadas", ignoreCase = true) == true
        
        return if (isSerie) {
            newTvSeriesSearchResponse(title, fixUrl(link), TvType.TvSeries) {
                this.posterUrl = poster?.let { fixUrl(it) }
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, fixUrl(link), TvType.Movie) {
                this.posterUrl = poster?.let { fixUrl(it) }
                this.year = year
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureLoggedIn()
        val url = "$mainUrl/search.php?q=$query"
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(url, headers = headers).document
        
        return document.select("div.items.break div.item.poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureLoggedIn()
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val title = document.selectFirst("div.info h1.fw-bolder")?.text()?.trim() ?: "Sem título"
        val poster = document.selectFirst("div.backImage")?.attr("style")?.let { style ->
            Regex("""background-image: url\(['"]([^'"]+)['"]\)""").find(style)?.groupValues?.get(1)
        }?.let { fixUrl(it) }
        
        val logSpans = document.select("p.log span").map { it.text().trim() }
        val year = logSpans.find { it.matches(Regex("\\d{4}")) }?.toIntOrNull()
        val duration = logSpans.find { it.contains("Min") }?.trim()
        val rating = logSpans.find { it.contains("IMDb") }?.replace("IMDb", "")?.trim()
        val quality = logSpans.find { it.contains("HD") }?.trim()
        val classification = document.selectFirst("p.log span em.classification")?.text()?.trim()
        
        val plot = document.selectFirst("p.small.linefive")?.text()?.trim()
        
        val genres = document.select("div.producerInfo p.lineone span span").map { it.text().trim() }.toMutableList()
        if (!quality.isNullOrBlank()) genres.add(0, "Qualidade: $quality")
        if (!duration.isNullOrBlank()) genres.add(0, "Duração: $duration")
        if (!rating.isNullOrBlank()) genres.add(0, "IMDb: $rating")
        if (!classification.isNullOrBlank()) genres.add(0, "Classificação: $classification")
        
        val recommendations = document.select("section.listContent div.swiper-slide.item.poster").mapNotNull { it.toSearchResult() }
        
        val hasSeasons = document.selectFirst("select#seasons-view") != null
        val isSerie = hasSeasons
        
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
        ensureLoggedIn()
        val episodes = mutableListOf<Episode>()
        
        val seasonOptions = document.select("select#seasons-view option")
        
        for (seasonOption in seasonOptions) {
            val seasonId = seasonOption.attr("value")
            val seasonName = seasonOption.text().trim()
            val seasonNumber = seasonName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
            
            val ajaxUrl = "$mainUrl/ajax/episodes.php?season=$seasonId"
            try {
                val ajaxDoc = app.get(ajaxUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                val episodeElements = ajaxDoc.select("div.ep")
                
                for (episodeElement in episodeElements) {
                    val episodeTitle = episodeElement.selectFirst("h5.fw-bold")?.text()?.trim() ?: continue
                    val episodeLink = episodeElement.selectFirst("a")?.attr("href") ?: continue
                    val episodeNumber = episodeElement.selectFirst("p[number]")?.attr("number")?.toIntOrNull() ?: 1
                    
                    val seasonEpisodeRegex = Regex("""(\\d+)x(\\d+)""")
                    val match = seasonEpisodeRegex.find(episodeTitle)
                    if (match != null) {
                        val extractedSeason = match.groupValues[1].toIntOrNull() ?: seasonNumber
                        val extractedEpisode = match.groupValues[2].toIntOrNull() ?: episodeNumber
                        
                        newEpisode(fixUrl(episodeLink)) {
                            this.name = episodeTitle
                            this.season = extractedSeason
                            this.episode = extractedEpisode
                        }.let { episodes.add(it) }
                    } else {
                        newEpisode(fixUrl(episodeLink)) {
                            this.name = episodeTitle
                            this.season = seasonNumber
                            this.episode = episodeNumber
                        }.let { episodes.add(it) }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return episodes
    }

    private fun getMovieSources(document: org.jsoup.nodes.Document, url: String): List<String> {
        val playerLink = document.selectFirst("div.buttons a[href*='playcinevs.info']")?.attr("href")
        return if (playerLink != null) listOf(playerLink) else emptyList()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureLoggedIn()
        val realData = when {
            data.startsWith("[") && data.endsWith("]") -> {
                data.removePrefix("[").removeSuffix("]").split(",").first().trim().removeSurrounding("\"")
            }
            else -> data
        }
        
        val playerDoc = app.get(realData, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val scripts = playerDoc.select("script")
        for (script in scripts) {
            val scriptContent = script.html()
            
            val videoMatch = Regex("""initializePlayer\(['"]([^'"]+\.mp4[^'"]*)['"]""").find(scriptContent)
            if (videoMatch != null) {
                val videoUrl = videoMatch.groupValues[1]
                callback(
                    newExtractorLink(
                        name,
                        "VisionCine Video",
                        videoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                    }
                )
                return true
            }
            
            val videoWithSubtitleMatch = Regex("""initializePlayerWithSubtitle\(['"]([^'"]+\.mp4[^'"]*)['"],\s*['"]([^'"]+\.srt[^'"]*)['"]""").find(scriptContent)
            if (videoWithSubtitleMatch != null) {
                val videoUrl = videoWithSubtitleMatch.groupValues[1]
                val subtitleUrl = videoWithSubtitleMatch.groupValues[2]
                
                callback(
                    newExtractorLink(
                        name,
                        "VisionCine Video",
                        videoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                    }
                )
                
                subtitleCallback(
                    SubtitleFile(
                        "pt",
                        subtitleUrl
                    )
                )
                return true
            }
            
            val videoMatch2 = Regex("""initializePlayer\(['"]([^'"]+\.mp4[^'"]*)['"],\s*['"]([^'"]*)['"],\s*(false|true),\s*(-?\d+)\)""").find(scriptContent)
            if (videoMatch2 != null) {
                val videoUrl = videoMatch2.groupValues[1]
                callback(
                    newExtractorLink(
                        name,
                        "VisionCine Video",
                        videoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                    }
                )
                return true
            }
        }
        
        return false
    }
} 