package com.NovelasFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NovelasFlix : MainAPI() {
    override var mainUrl = "https://novelasflix1.me"
    override var name = "NovelasFlix"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "top100.html" to "Top IMDB",
        "genero/acao/" to "Ação",
        "genero/animacao/" to "Animação",
        "genero/drama/" to "Drama",
        "genero/ficcao/" to "Ficção",
        "genero/ficcao-cientifica/" to "Ficção Científica",
        "genero/suspense/" to "Suspense",
        "genero/comedia/" to "Comédia",
        "genero/aventura/" to "Aventura",
        "genero/terror/" to "Terror"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document
        val home = document.select("div#dle-content div.default.poster.grid-item.has-overlay")
            .mapNotNull { it.toSearchResult() }
        
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
        val title = this.select("h3.poster__title a span").text().trim()
        val href = fixUrl(this.select("h3.poster__title a").attr("href"))
        val posterUrl = this.select("div.poster__img img").attr("src")
        val yearText = this.select("div.bslide__meta span").firstOrNull()?.text()?.trim()
        val year = yearText?.toIntOrNull()
        
        val isSeries = title.contains("S0", ignoreCase = true) || 
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true) ||
                      title.contains("Série", ignoreCase = true)
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?do=search"
        val data = mapOf(
            "do" to "search",
            "subaction" to "search",
            "search_start" to "0",
            "full_search" to "0",
            "result_from" to "1",
            "story" to query
        )
        
        val document = app.post(url, data = data).document
        return document.select("div#dle-content div.default.poster.grid-item.has-overlay")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val rawTitle = document.selectFirst("h1")?.text()?.trim() ?: ""
        val title = cleanTitle(rawTitle)
        
        val poster = document.selectFirst("div.movieposter img")?.attr("src")
        val description = extractDescription(document)
        val year = extractYear(document)
        val duration = extractDuration(document)
        val genres = extractGenres(document)
        val actors = extractActors(document)
        
        val hasSeasons = document.select("div.seasons-v2").isNotEmpty()
        val isSeries = hasSeasons || 
                      title.contains("S0", ignoreCase = true) ||
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true) ||
                      title.contains("Série", ignoreCase = true)
        
        return if (isSeries) {
            val episodes = loadEpisodesFromPage(document, url)
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
                this.tags = genres
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
                this.tags = genres
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return NovelasFlixExtractor.extractVideoLinks(data, mainUrl, name, callback)
    }
    
    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace("Assistir", "", ignoreCase = true)
            .replace("Online", "", ignoreCase = true)
            .replace("Grátis", "", ignoreCase = true)
            .replace("Gratis", "", ignoreCase = true)
            .trim()
            .replace(Regex("\\s+"), " ")
    }
    
    private fun extractDescription(document: org.jsoup.nodes.Document): String? {
        val descriptionElement = document.selectFirst("div.movie-description")
        return if (descriptionElement != null) {
            val paragraphs = descriptionElement.select("p").map { it.text().trim() }
            if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n")
            } else {
                descriptionElement.text().trim()
            }
        } else null
    }
    
    private fun extractYear(document: org.jsoup.nodes.Document): Int? {
        val yearText = document.selectFirst("p.yearof")?.text()?.replace("Ano:", "")?.trim()
        return yearText?.toIntOrNull()
    }
    
    private fun extractDuration(document: org.jsoup.nodes.Document): Int? {
        val durationElement = document.selectFirst("div.movie__meta span.duration")
        val durationText = durationElement?.selectFirst("span.tohr")?.text()?.trim()
        
        if (durationText != null) {
            return parseDuration(durationText)
        }
        
        val fullText = document.text()
        val durationRegex = Regex("Duração:\\s*(\\d+)hr\\s*(\\d+)\\s*min", RegexOption.IGNORE_CASE)
        val match = durationRegex.find(fullText)
        
        if (match != null) {
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return (hours * 60) + minutes
        }
        
        return null
    }
    
    private fun extractGenres(document: org.jsoup.nodes.Document): MutableList<String> {
        return document.select("div.onslide-cats a")
            .map { it.text().trim() }
            .filter { 
                val lower = it.lowercase()
                lower != "filme" && lower != "série" && lower != "serie" && 
                lower != "filmes" && lower != "séries" && lower != "series"
            }
            .distinct()
            .toMutableList()
    }
    
    private suspend fun extractActors(document: org.jsoup.nodes.Document): MutableList<Actor> {
        val actors = mutableListOf<Actor>()
        val actorsSection = document.selectFirst("div.credits p:contains(Atores:)")
        
        if (actorsSection != null) {
            val actorLinks = actorsSection.select("a.actor_link")
            for (actorLink in actorLinks) {
                val actorName = actorLink.text().trim()
                val actorUrl = actorLink.attr("href")
                
                try {
                    val fullActorUrl = if (actorUrl.startsWith("http")) actorUrl else "$mainUrl$actorUrl"
                    val actorDocument = app.get(fullActorUrl).document
                    val actorImage = actorDocument.selectFirst("div.movieposter img")?.attr("src")
                    actors.add(Actor(actorName, actorImage))
                } catch (e: Exception) {
                    actors.add(Actor(actorName))
                }
            }
        }
        
        return actors
    }
    
    private suspend fun loadEpisodesFromPage(document: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seasonLinks = document.select("div.seasons-v2 a.season-link")
        
        for (seasonLink in seasonLinks) {
            val seasonUrl = fixUrl(seasonLink.attr("href"))
            val seasonTitle = seasonLink.select("p.pstitle").text().trim()
            
            val seasonNumberMatch = Regex("S(\\d+)").find(seasonTitle) ?: Regex("Temporada (\\d+)").find(seasonTitle)
            val seasonNumber = seasonNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            
            try {
                val seasonDocument = app.get(seasonUrl).document
                val episodeLinks = seasonDocument.select("div.seasoncontent a.epi-link")
                
                for (episodeLink in episodeLinks) {
                    val episodeUrl = fixUrl(episodeLink.attr("href"))
                    val episodeNumberText = episodeLink.select("p.epiname").text().trim()
                    val episodeTitle = episodeLink.select("p.epinicename").text().trim()
                    val episodePoster = episodeLink.select("div.epiframe").attr("style")
                        .replace("background-image: url(", "")
                        .replace(")", "")
                    
                    val episodeNumberMatch = Regex("Serie (\\d+)").find(episodeNumberText)
                    val episodeNumber = episodeNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    
                    val episode = newEpisode(episodeUrl) {
                        this.name = if (episodeTitle.isNotBlank()) episodeTitle else "Episódio $episodeNumber"
                        this.episode = episodeNumber
                        this.season = seasonNumber
                        this.posterUrl = episodePoster
                    }
                    
                    episodes.add(episode)
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return episodes
    }
    
    private fun parseDuration(durationText: String): Int? {
        return try {
            val hoursMatch = Regex("(\\d+)hr").find(durationText)
            val minutesMatch = Regex("(\\d+) min").find(durationText)
            
            val hours = hoursMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val minutes = minutesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            (hours * 60) + minutes
        } catch (e: Exception) {
            null
        }
    }
} 