package com.BetterAnime

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

class BetterAnime : MainAPI() {
    override var mainUrl = "https://betteranime.io"
    override var name = "BetterAnime"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "categorias/acao" to "Ação",
        "categorias/aventura" to "Aventura",
        "categorias/fantasia" to "Fantasia",
        "categorias/misterio" to "Mistério",
        "categorias/ficcao-cientifica" to "Mistério"
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
    val linkElement = this.selectFirst("div.data h3 a") ?: return null
    val title = linkElement.text().trim()
    val href = fixUrl(linkElement.attr("href"))
    
    val posterUrl = this.selectFirst("div.poster img")?.attr("src")
    
    val isMovie = href.contains("/filme/") || href.contains("/movie/")
    val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
    
    val langText = this.select("div.languagem.box span").text().trim()
    val dubStatus = if (langText.contains("Dublado", ignoreCase = true)) {
        DubStatus.Dubbed
    } else {
        DubStatus.Subbed
    }

    return newAnimeSearchResponse(title, href, type) {
        this.posterUrl = posterUrl
        this.dubStatus = EnumSet.of(dubStatus)
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
        val description = document.select("div.wp-content p").text().trim()
        val year = extractYearFromDate(document.select("div.sheader div.data div.extra span.date").text())
        val tags = document.select("div.sheader div.data div.sgeneros a").map { it.text().trim() }

        val hasEpisodes = document.select("div#episodes").isNotEmpty()
        
        return if (!hasEpisodes) {
            val moviePoster = document.selectFirst("div.sheader div.poster img")?.attr("src")
            
            newMovieLoadResponse(title, url, TvType.AnimeMovie, url) {
                this.posterUrl = moviePoster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        } else {
            val episodesMap = loadEpisodesFromPage(document, url)

            val firstEpPoster = episodesMap.values.flatten().firstOrNull()?.posterUrl
            val mainPoster = firstEpPoster ?: document.selectFirst("div.sheader div.poster img")?.attr("src")

            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = mainPoster
                this.plot = description
                this.year = year
                this.tags = tags
                addEpisodes(DubStatus.Subbed, episodesMap[DubStatus.Subbed] ?: emptyList())
                addEpisodes(DubStatus.Dubbed, episodesMap[DubStatus.Dubbed] ?: emptyList())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return BetterAnimeExtractor.extractVideoLinks(data, mainUrl, name, callback)
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
    val subEpisodes = mutableListOf<Episode>()
    val dubEpisodes = mutableListOf<Episode>()
    
    val isPageDubbed = baseUrl.contains("dublado", ignoreCase = true) || 
                       document.select("span.quality").text().contains("Dublado", ignoreCase = true)

    val seasonElements = document.select("div#episodes div.se-c")
    
    for (seasonElement in seasonElements) {
        val seasonNumber = extractSeasonNumber(seasonElement.select("div.se-q span.title").text())
        val episodeElements = seasonElement.select("div.se-a ul.episodios li")
        
        for (episodeElement in episodeElements) {
            val aTag = episodeElement.selectFirst("div.episodiotitle a") ?: continue
            val episodeUrl = fixUrl(aTag.attr("href"))
            val episodeTitle = aTag.text().trim()
            
            val episodeImage = episodeElement.selectFirst("div.contentImg")?.attr("data-thumb")
                ?: episodeElement.selectFirst("img")?.attr("src")
                ?: episodeElement.selectFirst("div.coverImg")?.attr("style")?.let { 
                    Regex("""url\("?(.+?)"?\)""").find(it)?.groupValues?.get(1) 
                }

            val epNum = Regex("""(?i)episódio\s*(\d+)""").find(episodeTitle)?.groupValues?.get(1)?.toIntOrNull() 
                        ?: Regex("""(\d+)""").find(episodeTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val episode = newEpisode(episodeUrl) {
                this.name = episodeTitle
                this.episode = epNum
                this.season = seasonNumber
                this.posterUrl = episodeImage
            }
            
            if (isPageDubbed) dubEpisodes.add(episode) else subEpisodes.add(episode)
        }
    }

    if (subEpisodes.isNotEmpty()) episodes[DubStatus.Subbed] = subEpisodes.sortedBy { it.episode }
    if (dubEpisodes.isNotEmpty()) episodes[DubStatus.Dubbed] = dubEpisodes.sortedBy { it.episode }
    
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