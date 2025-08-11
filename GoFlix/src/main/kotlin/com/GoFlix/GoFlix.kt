package com.GoFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.toRatingInt
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.net.URLEncoder

class GoFlix : MainAPI() {
    override var mainUrl = "https://goflix3.lol"
    override var name = "GoFlix"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "categoria/lancamentos" to "Lançamentos",
        "categoria/acao" to "Ação",
        "categoria/animacao" to "Animação",
        "categoria/comedia" to "Comédia",
        "categoria/crime" to "Crime",
        "categoria/documentario" to "Documentário",
        "categoria/familia" to "Família",
        "categoria/ficcao-cientifica" to "Ficção-Científica",
        "categoria/nacional" to "Nacional",
        "categoria/terror" to "Terror"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}${if (page > 1) "/page/$page/" else "/"}"
        val doc = app.get(url).document
        val items = doc.select("ul.post-lst li article.post.movies")
        val list = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, list), hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("header.entry-header h2.entry-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("div.post-thumbnail figure img")?.attr("src")?.let {
            if (it.startsWith("//")) "https:$it" else it
        }
        val year = this.selectFirst("span.year")?.text()?.toIntOrNull()
        
        val type = when {
            this.selectFirst("span.watch")?.text()?.contains("Série") == true -> TvType.TvSeries
            else -> TvType.Movie
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/?s=$encodedQuery"
        val doc = app.get(url).document
        return doc.select("ul.post-lst li article.post.movies").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("article.post.single header.entry-header h1.entry-title")?.text()?.trim() ?: ""
        val description = doc.selectFirst("article.post.single div.description p")?.text()?.trim()
        val posterUrl = doc.selectFirst("div.bghd img.TPostBg")?.attr("src")?.let {
            if (it.startsWith("//")) "https:$it" else it
        }
        
        val year = doc.selectFirst("article.post.single header.entry-header div.entry-meta span.year")?.text()?.toIntOrNull()
        val duration = doc.selectFirst("article.post.single header.entry-header div.entry-meta span.duration")?.text()?.let {
            parseDuration(it)
        }
        val rating = doc.selectFirst("article.post.single footer div.vote-cn span.vote span.num")?.text()?.toRatingInt()
        
        val genres = doc.select("article.post.single header.entry-header div.entry-meta span.genres a")
            .mapNotNull { it.text() }
            .filter { !it.contains("Assistir Filmes") && !it.contains("Assistir Séries") }
        
        val actors = doc.select("article.post.single ul.cast-lst li").find { it.selectFirst("span")?.text() == "Elenco" }
            ?.select("p a")?.mapNotNull { it.text() } ?: emptyList()

        val trailerUrl = extractTrailerUrl(doc)
        val recommendations = getRecommendations(doc)

        if (url.contains("/series/")) {
            val seasons = getSeasons(doc, url)
            val episodes = mutableListOf<Episode>()
            
            seasons.forEach { (seasonNum, seasonId) ->
                val seasonEpisodes = getEpisodesFromSeason(seasonId, url, seasonNum)
                episodes.addAll(seasonEpisodes)
            }

            val response = newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = description
                this.year = year
                this.rating = rating
                this.tags = genres
                addActors(actors)
                this.duration = duration
                this.recommendations = recommendations
            }
            
            if (trailerUrl != null) {
                response.addTrailer(trailerUrl, mainUrl, false)
            }
            
            return response
        } else {
            val response = newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = description
                this.year = year
                this.rating = rating
                this.tags = genres
                addActors(actors)
                this.duration = duration
                this.recommendations = recommendations
            }
            
            if (trailerUrl != null) {
                response.addTrailer(trailerUrl, mainUrl, false)
            }
            
            return response
        }
    }

    private fun getSeasons(doc: Document, seriesUrl: String): List<Pair<Int, String>> {
        val seasons = mutableListOf<Pair<Int, String>>()
        val seasonElements = doc.select("section.section.episodes div.aa-drp ul.aa-cnt li.sel-temp a")
        
        seasonElements.forEach { element ->
            val seasonText = element.text()
            val seasonNum = Regex("Season (\\d+)").find(seasonText)?.groupValues?.get(1)?.toIntOrNull()
            val postId = element.attr("data-post")
            val seasonId = element.attr("data-season")
            
            if (seasonNum != null && postId.isNotEmpty() && seasonId.isNotEmpty()) {
                seasons.add(Pair(seasonNum, seasonId))
            }
        }
        
        return seasons
    }

    private suspend fun getEpisodesFromSeason(seasonId: String, seriesUrl: String, seasonNum: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        try {
            val postId = extractPostId(seriesUrl)
            val response = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to seriesUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                    "Cache-Control" to "no-cache",
                    "Origin" to mainUrl,
                    "Pragma" to "no-cache",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin"
                ),
                data = mapOf(
                    "action" to "action_select_season",
                    "season" to seasonId,
                    "post" to postId
                )
            ).document

            val episodeElements = response.select("li article.post.episodes")
            
            episodeElements.forEach { element ->
                val episodeTitle = element.selectFirst("header.entry-header h2.entry-title")?.text()?.trim()
                val episodeUrl = element.selectFirst("a.lnk-blk")?.attr("href")
                val episodeNum = element.selectFirst("header.entry-header span.num-epi")?.text()?.let {
                    Regex("\\d+x(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
                }
                val episodePoster = element.selectFirst("div.post-thumbnail figure img")?.attr("src")?.let {
                    if (it.startsWith("//")) "https:$it" else it
                }

                if (episodeTitle != null && episodeUrl != null && episodeNum != null) {
                    val episodeDetails = getEpisodeDetails(episodeUrl)
                    
                    episodes.add(newEpisode(episodeUrl) {
                        this.name = episodeTitle
                        this.season = seasonNum
                        this.episode = episodeNum
                        this.posterUrl = episodePoster
                        this.description = episodeDetails.first
                    })
                }
            }
        } catch (e: Exception) {
        }
        
        return episodes
    }

    private suspend fun getEpisodeDetails(episodeUrl: String): Pair<String?, String?> {
        return try {
            val doc = app.get(episodeUrl).document
            val description = doc.selectFirst("div.description")?.text()?.trim()
            val year = doc.selectFirst("header.entry-header div.entry-meta span.year")?.text()?.trim()
            Pair(description, year)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private suspend fun extractPostId(seriesUrl: String): String {
        return try {
            val doc = app.get(seriesUrl).document
            val seasonElement = doc.selectFirst("section.section.episodes div.aa-drp ul.aa-cnt li.sel-temp a")
            seasonElement?.attr("data-post") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTrailerUrl(doc: Document): String? {
        return try {
            val scriptContent = doc.select("script#funciones_public_js-js-extra").firstOrNull()?.data()
            
            scriptContent?.let { content ->
                val trailerMatch = Regex("\"trailer\":\"([^\"]+)\"").find(content)
                trailerMatch?.let { match ->
                    val trailerHtml = match.groupValues[1]
                        .replace("\\/", "/")
                        .replace("\\\"", "\"")
                    
                    val srcMatch = Regex("src=\"(https://www\\.youtube\\.com/embed/[^\"]+)\"").find(trailerHtml)
                    if (srcMatch != null) {
                        return srcMatch.groupValues[1]
                    }
                }
            }
            
            // Fallback: busca em toda a página por URLs do YouTube
            val pageContent = doc.html()
            val youtubeRegex = Regex("https://www\\.youtube\\.com/embed/[a-zA-Z0-9_-]+")
            val youtubeMatch = youtubeRegex.find(pageContent)
            youtubeMatch?.value
            
        } catch (e: Exception) {
            null
        }
    }

    private fun getRecommendations(doc: Document): List<SearchResponse> {
        val recommendations = mutableListOf<SearchResponse>()
        
        try {
            val recommendationElements = doc.select("div.owl-stage div.owl-item article.post")
            
            recommendationElements.forEach { element ->
                val title = element.selectFirst("header.entry-header h2.entry-title")?.text()?.trim()
                val href = element.selectFirst("a.lnk-blk")?.attr("href")
                val posterUrl = element.selectFirst("div.post-thumbnail figure img")?.attr("src")?.let {
                    if (it.startsWith("//")) "https:$it" else it
                }
                val year = element.selectFirst("span.year")?.text()?.toIntOrNull()
                val type = when {
                    element.selectFirst("span.watch")?.text()?.contains("Série") == true -> TvType.TvSeries
                    else -> TvType.Movie
                }
                
                if (title != null && href != null) {
                    val recommendation = newMovieSearchResponse(title, href, type) {
                        this.posterUrl = posterUrl
                        this.year = year
                    }
                    recommendations.add(recommendation)
                }
            }
            
            if (recommendations.isEmpty()) {
                val fallbackElements = doc.select("div.owl-stage-outer div.owl-stage div.owl-item article.post")
                fallbackElements.forEach { element ->
                    val title = element.selectFirst("header.entry-header h2.entry-title")?.text()?.trim()
                    val href = element.selectFirst("a.lnk-blk")?.attr("href")
                    val posterUrl = element.selectFirst("div.post-thumbnail figure img")?.attr("src")?.let {
                        if (it.startsWith("//")) "https:$it" else it
                    }
                    val year = element.selectFirst("span.year")?.text()?.toIntOrNull()
                    val type = when {
                        element.selectFirst("span.watch")?.text()?.contains("Série") == true -> TvType.TvSeries
                        else -> TvType.Movie
                    }
                    
                    if (title != null && href != null) {
                        val recommendation = newMovieSearchResponse(title, href, type) {
                            this.posterUrl = posterUrl
                            this.year = year
                        }
                        recommendations.add(recommendation)
                    }
                }
            }
            
        } catch (e: Exception) {
        }
        
        return recommendations
    }

    private fun parseDuration(durationText: String): Int? {
        val cleanText = durationText.replace("fa-clock", "").replace("far", "").trim()
        
        return when {
            cleanText.contains("h") && cleanText.contains("m") -> {
                val hours = Regex("(\\d+)h").find(cleanText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val minutes = Regex("(\\d+)m").find(cleanText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                hours * 60 + minutes
            }
            cleanText.contains("min") -> {
                Regex("(\\d+)").find(cleanText)?.groupValues?.get(1)?.toIntOrNull()
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val doc = app.get(data).document
            val videoOptions = doc.select("aside.video-player div.video iframe")
            
            var hasLinks = false
            
            videoOptions.forEach { iframe ->
                val videoUrl = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
                
                if (videoUrl.isNotEmpty() && videoUrl.contains("https://fembed.sx/e/")) {
                    val extractor = GoFlixExtractor()
                    extractor.getUrl(videoUrl, data, subtitleCallback, callback)
                    hasLinks = true
                }
            }
            
            hasLinks
        } catch (e: Exception) {
            false
        }
    }
    

}
