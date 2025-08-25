package com.BakashiTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.Actor
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.util.*
import kotlinx.coroutines.delay

class BakashiTV : MainAPI() {
    override var mainUrl = "https://bakashi.tv"
    override var name = "BakashiTV"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "genero/acao" to "Ação",
        "genero/comedia" to "Comédia",
        "genero/aventura" to "Aventura",
        "genero/fantasia" to "Fantasia",
        "genero/romance" to "Romance",
        "genero/misterio" to "Mistério",
        "genero/cotidiano" to "Cotidiano",
        "genero/escolar" to "Escolar",
        "genero/drama" to "Drama",
        "genero/esporte" to "Esporte"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) {
            "$mainUrl/${request.data}"
        } else {
            "$mainUrl/${request.data}?page=$page"
        }
        
        val document = app.get(url).document
        val home = document.select("div.items.full article.item.tvshows")
            .mapNotNull { it.toSearchResult() }
        
        val hasNext = document.select("div.pagination a[rel='next']").isNotEmpty()
        
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
        val title = this.select("div.data h3 a").text().trim()
        val href = fixUrl(this.select("div.data h3 a").attr("href"))
        val posterUrl = this.select("div.poster img").attr("src")
        val year = extractYearFromDate(this.select("div.data span").text().trim())
        
        val isMovie = href.contains("/filme/") || href.contains("/movie/")
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        
        return document.select("div.content.rigth.csearch div.result-item article")
            .mapNotNull { it.toSearchResultFromSearch() }
    }

    private fun Element.toSearchResultFromSearch(): SearchResponse? {
        val title = this.select("div.details div.title a").text().trim()
        val href = fixUrl(this.select("div.details div.title a").attr("href"))
        val posterUrl = this.select("div.image div.thumbnail img").attr("src")
        val year = this.select("div.details div.meta span.year").text().trim().toIntOrNull()
        
        val isMovie = href.contains("/filme/") || href.contains("/movie/")
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.sheader div.data h1")?.text()?.trim() ?: ""
        val description = extractDescription(document)
        val year = extractYear(document)
        val genres = extractGenres(document)
        val poster = extractPoster(document)
        
        val hasEpisodesSection = document.select("div#episodes").isNotEmpty()
        val type = if (hasEpisodesSection) TvType.Anime else TvType.AnimeMovie
        
        return if (type == TvType.Anime) {
            val episodes = loadEpisodesFromPage(document, url)
            newAnimeLoadResponse(title, url, type) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genres
                addEpisodes(DubStatus.Subbed, episodes[DubStatus.Subbed] ?: emptyList())
                addEpisodes(DubStatus.Dubbed, episodes[DubStatus.Dubbed] ?: emptyList())
            }
        } else {
            newMovieLoadResponse(title, url, type, url) {
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
        return BakashiTVExtractor.extractVideoLinks(data, mainUrl, name, callback)
    }
    
    private fun extractDescription(document: Document): String? {
        return document.select("div.wp-content p").text().trim()
    }
    
    private fun extractYear(document: Document): Int? {
        val dateText = document.select("div.sheader div.data div.extra span.date").text().trim()
        return extractYearFromDate(dateText)
    }
    
    private fun extractGenres(document: Document): MutableList<String> {
        return document.select("div.sheader div.data div.sgeneros a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()
    }
    
    private fun extractPoster(document: Document): String? {
        val posterElement = document.selectFirst("div.sheader div.poster img")
        return posterElement?.attr("src")
    }
    
    private suspend fun loadEpisodesFromPage(document: Document, baseUrl: String): MutableMap<DubStatus, List<Episode>> {
        val episodes = mutableMapOf<DubStatus, List<Episode>>()
        val allEpisodes = mutableListOf<Episode>()
        
        val seasonElements = document.select("div#episodes div#serie_contenido div#seasons div.se-c")
        
        for (seasonElement in seasonElements) {
            val seasonText = seasonElement.select("div.se-q span.title").text().trim()
            val seasonNumber = extractSeasonNumber(seasonText)
            
            val episodeElements = seasonElement.select("div.se-a ul.episodios li")
            
            for (episodeElement in episodeElements) {
                val episodeUrl = fixUrl(episodeElement.select("div.episodiotitle a").attr("href"))
                val episodeTitle = episodeElement.select("div.episodiotitle a").text().trim()
                
                val episodeImage = episodeElement.select("div.imagen div.contentImg img").attr("src")
                
                val episodeNumber = extractEpisodeNumber(episodeTitle)
                
                if (episodeTitle.isNotEmpty() && episodeNumber > 0) {
                    val episode = newEpisode(episodeUrl) {
                        this.name = episodeTitle
                        this.episode = episodeNumber
                        this.season = seasonNumber
                        this.posterUrl = episodeImage
                    }
                    
                    allEpisodes.add(episode)
                }
            }
        }
        
        val dubStatus = if (baseUrl.contains("dublado", ignoreCase = true)) {
            DubStatus.Dubbed
        } else {
            DubStatus.Subbed
        }
        
        episodes[dubStatus] = allEpisodes.sortedWith(compareBy({ it.season }, { it.episode }))
        return episodes
    }
    
    private fun extractSeasonNumber(text: String): Int {
        val seasonMatch = Regex("""Temporada\s*(\d+)""").find(text)
        return seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
    
    private fun extractEpisodeNumber(text: String): Int {
        val episodeMatch = Regex("""(\d+)\s*-\s*Episódio""").find(text)
        return episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
    
    private fun extractYearFromDate(dateText: String): Int? {
        val yearMatch = Regex("""(\d{4})""").find(dateText)
        return yearMatch?.groupValues?.get(1)?.toIntOrNull()
    }
}
