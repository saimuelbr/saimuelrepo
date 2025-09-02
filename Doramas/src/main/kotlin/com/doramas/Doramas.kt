package com.doramas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Doramas : MainAPI() {
    override var mainUrl = "https://doramasonline.co"
    override var name = "Doramas"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/lancamentos/" to "Lançamentos",
        "$mainUrl/category/animacao/" to "Animação",
        "$mainUrl/category/comedia/" to "Comédia",
        "$mainUrl/category/crime/" to "Crime",
        "$mainUrl/category/documentario/" to "Documentário",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/familia/" to "Família",
        "$mainUrl/category/misterio/" to "Mistério"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + if (page > 1) "page/$page/" else "").document
        val home = document.select("div.aa-cn div#movies-a ul.post-lst li").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("header.entry-header h2.entry-title")?.text() ?: return null
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: return null
        
        val posterUrl = this.selectFirst("div.post-thumbnail figure img")?.let { img ->
            val src = img.attr("src").takeIf { it.isNotBlank() } ?: img.attr("data-src")
            src?.let { url ->
                val fullUrl = if (url.startsWith("//")) "https:$url" else url
                fullUrl.replace("/w500/", "/original/")
            }
        }
        
        val year = this.selectFirst("span.year")?.text()?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.year = year
            this.quality = getQualityFromString(this@toSearchResult.selectFirst("span.post-ql")?.text())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = app.get("$mainUrl/?s=$query").document
        return searchResponse.select("div.aa-cn div#movies-a ul.post-lst li").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("aside.fg1 header.entry-header h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst("div.bghd img.TPostBg")?.let { img ->
            val src = img.attr("src").takeIf { it.isNotBlank() } ?: img.attr("data-src")
            src?.let { url ->
                val fullUrl = if (url.startsWith("//")) "https:$url" else url
                fullUrl.replace("/w1280/", "/original/")
            }
        }
        val year = document.selectFirst("aside.fg1 header.entry-header div.entry-meta span.year")?.text()?.substringAfter("far\">")?.toIntOrNull()
        val duration = document.selectFirst("aside.fg1 header.entry-header div.entry-meta span.duration")?.text()?.substringAfter("far\">")
        val rating = document.selectFirst("div.vote-cn span.vote span.num")?.text()?.toDoubleOrNull()
        val plot = document.selectFirst("aside.fg1 div.description p")?.text()
        val genres = document.select("aside.fg1 header.entry-header div.entry-meta span.genres a").map { it.text() }
        
        val actors = document.selectFirst("aside.fg1 ul.cast-lst p")?.select("a")?.map { 
            Actor(it.text(), it.attr("href"))
        }
        
        val trailerUrl = document.selectFirst("div.mdl-cn div.video iframe")?.let { iframe ->
            iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
        }

        val iframeElement = document.selectFirst("iframe[src*='assistirseriesonline']")
        val iframeUrl = iframeElement?.let { iframe ->
            iframe.attr("src").takeIf { it.isNotBlank() } ?: iframe.attr("data-src")
        }

        val isSerie = url.contains("/serie/")
        
        return if (isSerie) {
            if (iframeUrl != null) {
                val iframeDocument = app.get(iframeUrl).document
                val episodes = parseSeriesEpisodes(iframeDocument, iframeUrl)
                
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    this.rating = rating?.times(1000)?.toInt()
                    this.tags = genres
                    if (actors != null) addActors(actors)
                    addTrailer(trailerUrl)
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    this.rating = rating?.times(1000)?.toInt()
                    this.tags = genres
                    if (actors != null) addActors(actors)
                    addTrailer(trailerUrl)
                }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, iframeUrl ?: "") {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.rating = rating?.times(1000)?.toInt()
                this.tags = genres
                this.duration = parseDuration(duration)
                if (actors != null) addActors(actors)
                addTrailer(trailerUrl)
            }
        }
    }

    private suspend fun parseSeriesEpisodes(iframeDocument: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seasons = iframeDocument.select("header.header ul.header-navigation li")
        
        for (seasonElement in seasons) {
            val seasonNumber = seasonElement.attr("data-season-number").toIntOrNull() ?: continue
            val seasonId = seasonElement.attr("data-season-id")
            
            val seasonEpisodes = iframeDocument.select("li[data-season-id='$seasonId']")
                .mapNotNull { episodeElement ->
                    val episodeId = episodeElement.attr("data-episode-id")
                    val episodeTitle = episodeElement.selectFirst("a")?.text() ?: return@mapNotNull null
                    
                    val episodeNumber = episodeTitle.substringBefore(" - ").toIntOrNull() ?: 1
                    val cleanTitle = if (episodeTitle.contains(" - ")) {
                        episodeTitle.substringAfter(" - ")
                    } else {
                        episodeTitle
                    }
                    
                    Episode(
                        data = episodeId,
                        name = cleanTitle,
                        season = seasonNumber,
                        episode = episodeNumber
                    )
                }
            
            episodes.addAll(seasonEpisodes)
        }
        
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        return if (data.matches(Regex("\\d+"))) {
            loadFromPage(
                "https://assistirseriesonline.icu/episodio/$data",
                subtitleCallback,
                callback
            )
        } else {
            loadFromPage(data, subtitleCallback, callback)
        }
    }

    private suspend fun loadFromPage(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(pageUrl).document

        return findEmbedLink(doc).map { url ->
            loadExtractor(url, pageUrl, subtitleCallback, callback)
        }.isEmpty()
    }

    private fun findEmbedLink(doc: Document): List<String> {

        val fromButtons = doc.select("#players button[data-source]")
            .mapNotNull { it.attr("data-source").takeIf { src -> src.isNotBlank() } }

        if (fromButtons.isNotEmpty()) {
            return fromButtons
        }

        val scriptText = doc.select("script").html()
        val regex = Regex("\"source\":\"(.*?)\"")

        val fromScript = regex.findAll(scriptText)
            .map { it.groupValues[1].replace("\\/", "/") }
            .toList()

        return fromScript
    }

    private fun parseDuration(duration: String?): Int? {
        if (duration == null) return null
        val regex = Regex("(\\d+)h\\s*(\\d+)m")
        val matchResult = regex.find(duration)
        return if (matchResult != null) {
            val hours = matchResult.groupValues[1].toIntOrNull() ?: 0
            val minutes = matchResult.groupValues[2].toIntOrNull() ?: 0
            hours * 60 + minutes
        } else {
            val minutesRegex = Regex("(\\d+)m")
            val minutesMatch = minutesRegex.find(duration)
            minutesMatch?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
