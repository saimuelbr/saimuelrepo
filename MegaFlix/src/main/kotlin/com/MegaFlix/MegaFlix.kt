package com.MegaFlix

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

class MegaFlix : MainAPI() {

    override var name = "MegaFlix"
    override var lang = "pt-br"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var mainUrl = "https://megaflix.lat"

    private fun getUrl(url: String): String {
        val uri = URI(url)
        return "${mainUrl}${uri.path ?: ""}${if (uri.query != null) "?${uri.query}" else ""}"
    }


    override val mainPage = mainPageOf(
        "/genero/acao" to "Ação",
        "/genero/animacao" to "Animação",
        "/genero/comedia" to "Comédia",
        "/genero/crime" to "Crime",
        "/genero/documentario" to "Documentário",
        "/genero/drama" to "Drama",
        "/genero/familia" to "Família",
        "/genero/fantasia" to "Fantasia",
        "/genero/faroeste" to "Faroeste",
        "/genero/guerra" to "Guerra",
        "/genero/misterio" to "Mistério",
        "/genero/sci-fi" to "Sci-fi",
        "/genero/thiller" to "Thriller"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val cookies: MutableMap<String, String> = mutableMapOf()

        cookies["ordem"] = "3"

        val soup = app.get(
            getUrl("${request.data}/${page}"),
            headers = mapOf("User-Agent" to USER_AGENT),
            cookies = cookies
        ).document

        val home = soup.select("div.col-lg-2 > a").mapNotNull { item ->
            val title = item.selectFirst("h3.title")?.text() ?: ""
            val link = item.selectFirst("a")?.attr("href") ?: ""
            val postUrl = item.select("picture img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: item.select("img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: ""

            val extensions =
                listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", ".tiff")

            val containsExtension = extensions.any { ext -> postUrl.endsWith(ext) }

            if (!containsExtension) {
                null
            } else {
                newMovieSearchResponse(
                    title,
                    link,
                    if (link.contains("filme")) TvType.Movie else TvType.TvSeries,
                ) {
                    this.posterUrl = postUrl
                }
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val soup = app.get("${mainUrl}/procurar/${query}").document

        val home = soup.select("div.col-lg-2 > a").mapNotNull {
            val title = it.selectFirst("h3.title")?.text() ?: ""
            val link = it.selectFirst("a")?.attr("href") ?: ""
            val postUrl = it.selectFirst("img")?.attr("src") ?: ""

            val extensions =
                listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", ".tiff")

            val containsExtension = extensions.any { ext -> postUrl.endsWith(ext) }

            if (!containsExtension) {
                null
            } else {
                newTvSeriesSearchResponse(
                    title,
                    link,
                    if (link.contains("filme")) TvType.Movie else TvType.TvSeries,
                ) {
                    this.posterUrl = postUrl
                }
            }
        }

        return home
    }

    override suspend fun load(url: String): LoadResponse {

        val type = getType(url)

        val document = app.get(getUrl(url)).document

        val title = document.selectFirst("h1.h3.mb-1")!!.text()
        val descipt = document.selectFirst("p.fs-sm.text-muted")!!.text()
        val rating =
            document.selectFirst("div.text-imdb > span")?.text()
        val year = document.selectFirst("li.list-inline-item")?.text()
        val backgroundPoster =
            fixUrlNull(document.selectFirst("img.img-fluid")?.attr("src"))

        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()

            document.select("div.card-season div.accordion-item div.select-season")
                .forEach { element ->
                    val season = element.attr("data-season").toIntOrNull()
                    val item = element.attr("data-item")
                    if (season != null && season > 0 && !item.isNullOrBlank()) {
                        list.add(Pair(season, item))
                    }
                }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            val url1 = extracturl(url)

            for (season in list) {
                val seasonResponse = app.post(
                    "${mainUrl}/api/seasons",
                    data = mapOf(
                        "season" to "${season.first}",
                        "item_id" to season.second,
                        "item_url" to url1.toString()
                    )
                ).document

                val episodes = seasonResponse.select("div.card-episode")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->

                        val ep = episode.selectFirst("a.episode")
                        val epNum = extractNumber(ep?.text().toString())
                        val name = episode.selectFirst("a.name")?.text()
                        val href = ep?.attr("href")

                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = season.first
                                this.episode = epNum
                            }
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(
                title, url, TvType.TvSeries, episodeList
            ) {
                posterUrl = backgroundPoster
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.score = Score.from10(rating)
            }
        } else {

            val players = document.select("ul.players li a").map {
                it.attr("data-url")
            }

            return newMovieLoadResponse(
                title,
                url,
                type,
                players
            ) {
                posterUrl = backgroundPoster
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (isUrl(data)) {
            val document = app.get(getUrl(data)).document
            document.select("ul.players li a").map {
                val cleanUrl =
                    it.attr("data-url").replace("https://megafrixapi.com/blog/index.php?link=", "")

                loadExtractor(cleanUrl, cleanUrl, subtitleCallback, callback)
            }
        } else {
            val urls = data.trim('[', ']').split(',').map { it.replace("\"", "").trim() }
            urls.forEach { url ->

                val cleanUrl = url.replace("https://megafrixapi.com/blog/index.php?link=", "")

                loadExtractor(cleanUrl, cleanUrl, subtitleCallback, callback)
            }
        }
        return data.isEmpty()
    }

    private fun getType(t: String): TvType {
        return when {
            t.contains("filme") -> TvType.Movie
            else -> TvType.TvSeries
        }
    }

    private fun extrairLink(input: String): String? {
        val regex = """window\.location\.href\s*=\s*["'](.*?)["']\s*;?""".toRegex()
        val matchResult = regex.find(input)
        return matchResult?.groups?.get(1)?.value
    }

    fun extractNumber(text: String): Int? {
        val regex = """(\d+)""".toRegex()
        val matchResult = regex.find(text)
        return matchResult?.groups?.get(1)?.value?.toInt()
    }

    private fun extracturl(url: String): String? {
        val regex = """.*/assistir/([^/]+)[/?]?.*""".toRegex()
        val matchResult = regex.find(url)
        return matchResult?.groups?.get(1)?.value
    }

    fun isUrl(text: String): Boolean {
        val regex = """^(http(s)?://)[^\s]+?\.[^\s]+/?""".toRegex()
        return regex.matches(text)
    }
}