package com.FilmesOn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import org.jsoup.nodes.Element

class FilmesOn : MainAPI() {

    override var mainUrl = "https://filmeson1.site"
    override var name = "FilmesOn"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "genero/acao" to "Ação",
        "genero/aventura" to "Aventura",
        "genero/ficcao-cientifica" to "Ficção Científica",
        "genero/comedia" to "Comédia",
        "genero/drama" to "Drama",
        "genero/terror" to "Terror",
        "genero/romance" to "Romance",
        "genero/animacao" to "Animação"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}${if (page > 1) "/page/$page/" else "/"}"
        val document = app.get(url).document
        val home = document.select("div.items.full article.item").mapNotNull { it.toSearchResult() }
        
        return newHomePageResponse(request.name, home, document.select("div.pagination a.arrow_pag").isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document

        return document.select("div.result-item article").mapNotNull { el ->
            val title = el.select("div.details div.title a").text().trim()
            val href = fixUrl(el.select("div.details div.title a").attr("href"))
            val poster = el.select("div.image img").attr("src").replace("/w92/", "/original/")
            val isSeries = href.contains("/series/")

            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val contentInfo = document.extractContentInfo()
        val isSeries = url.contains("/series/")

        return if (isSeries) {
            val episodes = document.extractEpisodes()
            newTvSeriesLoadResponse(contentInfo.title, url, TvType.TvSeries, episodes) {
                applyMetadata(contentInfo)
            }
        } else {
            newMovieLoadResponse(contentInfo.title, url, TvType.Movie, url) {
                applyMetadata(contentInfo)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val playerOptions = document.select("ul#playeroptionsul li.dooplay_player_option")
        val extractor = FilmesOnExtractor(mainUrl, this.name)
        return extractor.processPlayerOptions(playerOptions, data, subtitleCallback, callback)
    }

    private fun TvSeriesLoadResponse.applyMetadata(info: ContentInfo) {
        this.posterUrl = info.poster
        this.backgroundPosterUrl = info.background
        this.year = info.year
        this.plot = info.plot
        this.duration = info.duration
        this.tags = info.genres + info.tags
        this.score = info.score?.let { Score.from10(it) }
        addActors(info.actors)
    }

    private fun MovieLoadResponse.applyMetadata(info: ContentInfo) {
        this.posterUrl = info.poster
        this.backgroundPosterUrl = info.background
        this.year = info.year
        this.plot = info.plot
        this.duration = info.duration
        this.tags = info.genres + info.tags
        this.score = info.score?.let { Score.from10(it) }
        addActors(info.actors)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = select("div.data h3 a").text().trim()
        val href = fixUrl(select("div.data h3 a").attr("href"))
        val poster = select("div.poster img").attr("src").replace("w185", "original")
        val isSeries = hasClass("tvshows") || href.contains("/series/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    private fun Element.extractContentInfo(): ContentInfo {
        val title = selectFirst("h1")?.text()?.trim() ?: ""
        val poster = selectFirst("div.poster img")?.attr("src")
        val background = Regex("""class="g-item">\s*<a\s+href="([^"]+original[^"]+)""")
            .find(html())?.groupValues?.get(1)
        
        val genres = select("div.sgeneros a").map { it.text().trim() }
        val tags = select("div.extra span").map { it.text().trim() }.filter { it.matches(Regex("[A-Z]+-\\d+")) }
        
        val year = select("div.extra span.date").text().trim().let { Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val plot = select("div.wp-content blockquote p").text().trim()
        
        val scoreText = selectFirst("b#repimdb strong")?.text() 
            ?: select("div.custom_fields:contains(TMDb) strong").text()

        val scoreValue = scoreText.trim().replace(",", ".").toDoubleOrNull()
        
        val durationText = select("div.extra span.runtime").text()
        val duration = Regex("(\\d+)").find(durationText)?.groupValues?.get(1)?.toIntOrNull()

        val actors = select("div#cast div.persons div.person").mapNotNull { 
            val name = it.select("div.data div.name a").text().trim()
            val role = it.select("div.data div.caracter").text().trim()
            val img = it.select("div.img img").attr("src")
            if (role.equals("Director", true)) null else Actor(name, img)
        }

        return ContentInfo(title, poster, background, genres, tags, year, plot, scoreValue, duration, actors)
    }

    private fun Element.extractEpisodes(): List<Episode> {
        return select("div#serie_contenido div.se-c").flatMap { season ->
            val seasonNum = season.select("span.se-t").text().toIntOrNull() ?: 1
            season.select("ul.episodios li").map { ep ->
                val epNum = ep.select("div.numerando").text().split(" - ").lastOrNull()?.toIntOrNull() ?: 1
                val name = ep.select("div.episodiotitle a").text().trim()
                val url = fixUrl(ep.select("div.episodiotitle a").attr("href"))
                val poster = ep.selectFirst("div.imagen img")?.let { 
                    it.attr("src").ifBlank { it.attr("data-lazy-src") } 
                }?.replace("/w154/", "/original/")

                newEpisode(url) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = poster
                }
            }
        }
    }
}

data class ContentInfo(
    val title: String,
    val poster: String?,
    val background: String?,
    val genres: List<String>,
    val tags: List<String>,
    val year: Int?,
    val plot: String,
    val score: Double?,
    val duration: Int?,
    val actors: List<Actor>
)