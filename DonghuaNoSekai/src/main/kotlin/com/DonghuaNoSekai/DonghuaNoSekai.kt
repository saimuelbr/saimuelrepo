package com.DonghuaNoSekai

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.Actor
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.util.*

data class AjaxResponse(
    val page: Int,
    val total_results: Int,
    val mensagem: String,
    val total_page: Int,
    val results: List<String>
)

class DonghuaNoSekai : MainAPI() {
    override var mainUrl = "https://donghuanosekai.com"
    override var name = "DonghuaNoSekai"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "acao" to "Ação",
        "artes-marciais" to "Artes Marciais",
        "comedia" to "Comédia",
        "fantasia" to "Fantasia",
        "harem" to "Hárem",
        "magia" to "Magia",
        "misterio" to "Mistério",
        "romance" to "Romance"
    )

    private val genreFilters = mapOf(
        "acao" to "2",
        "artes-marciais" to "54",
        "comedia" to "16",
        "fantasia" to "392",
        "harem" to "111",
        "magia" to "17",
        "misterio" to "149",
        "romance" to "12"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val genreId = genreFilters[request.data] ?: "2"
        val filters = buildString {
            append("%7B%22filter_data%22%3A%22filter_letter%3D0%26type_url%3DONA%26filter_status%3Dall%26filter_animation%3Dall%26filter_audio%3Dundefined%26filter_order%3Dname%22%2C%22filter_genre_add%22%3A%5B%22")
            append(genreId)
            append("%22%5D%2C%22filter_genre_del%22%3A%5B%5D%7D")
        }
        
        val payload = mapOf(
            "action" to "getListFilter",
            "token" to "d01c3e1cc8",
            "pagina" to page.toString(),
            "search" to "0",
            "limit" to "30",
            "type" to "lista",
            "filters" to filters
        )
        
        val response = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = payload,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/donghuas"
            )
        )
        
        val jsonResponse = response.parsedSafe<AjaxResponse>()
        val htmlContent = jsonResponse?.results?.joinToString("") ?: ""
        
        val document = org.jsoup.Jsoup.parse(htmlContent)
        val home = document.select("div.itemE.capa")
            .mapNotNull { it.toSearchResult() }
        
        val hasNext = jsonResponse?.page ?: 0 < (jsonResponse?.total_page ?: 0)
        
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
        val title = this.select("div.dados div.title h3 span.sxcd").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("div.thumb img").attr("src")
        val status = this.select("div.status").text().trim()
        val type = this.select("div.selos").attr("data-ona")
        
        val isMovie = type == "Movie" || type == "Special"
        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return newAnimeSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        
        return document.select("div.b_flex.b_wrap div.itemE.capa")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.dados h1")?.text()?.trim() ?: ""
        val description = extractDescription(document)
        val year = extractYear(document)
        val duration = extractDuration(document)
        val genres = extractGenres(document)
        val poster = extractPoster(document)
        
        val typeText = document.select("ul.b_flex.b_wrap.b_space_between li").find { 
            it.text().contains("Tipo:") 
        }?.text()?.trim()
        
        val hasEpisodesSection = document.select("div.episode_list").isNotEmpty()
        val isMovie = typeText?.contains("Movie") == true || !hasEpisodesSection
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return if (type == TvType.Anime) {
            val episodes = loadEpisodesFromPage(document, url)
            newAnimeLoadResponse(title, url, type) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
                this.tags = genres
                addEpisodes(DubStatus.Subbed, episodes)
            }
        } else {
            newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
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
        return DonghuaNoSekaiExtractor.extractVideoLinks(data, mainUrl, name, callback)
    }
    
    private fun extractDescription(document: Document): String? {
        return document.select("div.context p")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && !it.contains("PATREON", ignoreCase = true) }
            .joinToString("\n\n")
            .takeIf { it.isNotEmpty() }
    }
    
    private fun extractYear(document: Document): Int? {
        return document.select("ul.b_flex.b_wrap.b_space_between li")
            .find { it.text().contains("Ano:") }
            ?.text()
            ?.let { text ->
                YEAR_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
            }
    }
    
    private fun extractDuration(document: Document): Int? {
        return document.select("ul.b_flex.b_wrap.b_space_between li")
            .find { it.text().contains("Duração:") }
            ?.text()
            ?.let { text ->
                when {
                    text.contains("1h") -> {
                        val minutes = DURATION_HOURS_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        60 + minutes
                    }
                    text.contains("min") -> {
                        DURATION_MINUTES_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    }
                    else -> null
                }
            }
    }
    
    private fun extractGenres(document: Document): MutableList<String> {
        return document.select("div.genresL a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()
    }
    
    private fun extractPoster(document: Document): String? {
        return document.selectFirst("div.mew")
            ?.attr("style")
            ?.let { style ->
                POSTER_REGEX.find(style)?.groupValues?.get(1)
            }
    }
    
    private suspend fun loadEpisodesFromPage(document: Document, baseUrl: String): List<Episode> {
        return document.select("div.episode_list div.item")
            .mapNotNull { episodeElement ->
                val episodeUrl = fixUrl(episodeElement.select("a").attr("href"))
                val episodeTitle = episodeElement.select("div.dados div.title h3 span.anime").text().trim()
                val episodeText = episodeElement.select("div.dados div.title h3 span.episode").text().trim()
                
                val episodeNumbers = extractEpisodeNumbers(episodeText)
                val seasonNumber = extractSeasonNumber(episodeTitle)
                
                episodeNumbers.mapNotNull { episodeNumber ->
                    if (episodeNumber > 0) {
                        newEpisode(episodeUrl) {
                            this.name = episodeText
                            this.episode = episodeNumber
                            this.season = seasonNumber
                        }
                    } else null
                }
            }
            .flatten()
            .sortedWith(compareBy({ it.season }, { it.episode }))
    }
    
    companion object {
        private val SEASON_REGEX = Regex("""(\d+)[aª]?\s*Temporada""")
        private val EPISODE_RANGE_REGEX = Regex("""Episódio\s*(\d+)\s*(?:e\s*(\d+)|a\s*(\d+))?""")
        private val EPISODE_SIMPLE_REGEX = Regex("""Episódio\s*(\d+)""")
        private val YEAR_REGEX = Regex("""Ano:\s*(\d{4})""")
        private val DURATION_HOURS_REGEX = Regex("""(\d+)min""")
        private val DURATION_MINUTES_REGEX = Regex("""(\d+)\s*min""")
        private val POSTER_REGEX = Regex("""url\(([^)]+)\)""")
    }
    
    private fun extractSeasonNumber(text: String): Int {
        return SEASON_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
    
    private fun extractEpisodeNumbers(text: String): List<Int> {
        val episodeNumbers = mutableListOf<Int>()
        
        val episodeMatch = EPISODE_RANGE_REGEX.find(text)
        if (episodeMatch != null) {
            val firstEpisode = episodeMatch.groupValues[1].toIntOrNull() ?: 0
            episodeNumbers.add(firstEpisode)
            
            val secondEpisode = episodeMatch.groupValues[2].toIntOrNull()
            if (secondEpisode != null) {
                episodeNumbers.add(secondEpisode)
            }
            
            val lastEpisode = episodeMatch.groupValues[3].toIntOrNull()
            if (lastEpisode != null && lastEpisode > firstEpisode) {
                for (i in (firstEpisode + 1)..lastEpisode) {
                    episodeNumbers.add(i)
                }
            }
        } else {
            val simpleMatch = EPISODE_SIMPLE_REGEX.find(text)
            val episodeNumber = simpleMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (episodeNumber > 0) {
                episodeNumbers.add(episodeNumber)
            }
        }
        
        return episodeNumbers.distinct().sorted()
    }
}
