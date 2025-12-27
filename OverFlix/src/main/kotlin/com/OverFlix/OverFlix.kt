package com.OverFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class OverFlix : MainAPI() {

    companion object {
        private const val BASE_URL = "https://www.overflix.me"
        private const val LANG = "pt-br"
        private val UA_HEADERS = mapOf("User-Agent" to USER_AGENT)
    }

    override var mainUrl = BASE_URL
    override var name = "OverFlix"
    override val hasMainPage = true
    override var lang = LANG
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "/filmes/acao" to "Filmes - Ação",
        "/series/action-and-adventure" to "Séries - Ação",
        "/filmes/animacao" to "Filmes - Animação",
        "/series/animacao" to "Séries - Animação",
        "/filmes/comedia" to "Filmes - Comédia",
        "/series/comedia" to "Séries - Comédia",
        "/filmes/crime" to "Filmes - Crime",
        "/series/crime" to "Séries - Crime",
        "/filmes/ficcao-cientifica" to "Filmes - Ficção-Cientifíca",
        "/series/sci-fi-and-fantasy" to "Séries - Ficção-Cientifíca",
        "/filmes/drama" to "Filmes - Drama",
        "/series/drama" to "Séries - Drama"
    )

    private fun Element.posterUrl(): String {
        val img = selectFirst("img") ?: return ""
        val src = img.attr("src")
        return if (src.contains("transparent") || src.isBlank()) img.attr("data-src") else src
    }

    private fun Element.backdropUrl(): String? {
        return select("figure img.aspect-video")
            .attr("src")
            .ifBlank { null }
            ?.replace("/w300/", "/original/")
            ?.replace("/w1280/", "/original/")
    }

    private fun buildData(link: String, backdrop: String?): String {
        return if (backdrop != null) "$link|BACKDROP|$backdrop" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) "$mainUrl${request.data}" else "$mainUrl${request.data}?page=$page"
        val document = app.get(url, headers = UA_HEADERS).document

        val items = document.select("article[class*='group/item']").mapNotNull { item ->
            val link = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = item.selectFirst("h2")?.text()
                ?: item.selectFirst("img")?.attr("alt")?.replace(" poster", "")
                ?: return@mapNotNull null

            val isMovie = link.contains("/filme/")
            val type = if (isMovie) TvType.Movie else TvType.TvSeries
            val backdrop = if (isMovie) item.backdropUrl() else null
            val data = buildData(link, backdrop)

            newMovieSearchResponse(title, data, type) {
                posterUrl = item.posterUrl()
            }
        }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/pesquisa?s=$query").document

        return document.select("article.relative.group\\/item").mapNotNull { item ->
            val link = item.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = item.selectFirst("h2")?.text()
                ?: item.selectFirst("img")?.attr("alt")
                ?: return@mapNotNull null

            val isMovie = link.contains("/filme/")
            val type = if (isMovie) TvType.Movie else TvType.TvSeries
            val backdrop = if (isMovie) item.backdropUrl() else null
            val data = buildData(link, backdrop)

            newMovieSearchResponse(title.trim(), data, type) {
                posterUrl = item.posterUrl()
                year = item.select("div.text-subs span")
                    .firstOrNull { it.text().matches(Regex("\\d+")) }
                    ?.text()
                    ?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|BACKDROP|")
        val realUrl = parts[0]
        val forcedBackdrop = parts.getOrNull(1)

        val document = app.get(realUrl).document
        val isMovie = realUrl.contains("/filme/")
        val title = document.selectFirst("h1.text-3xl, h2.text-3xl")?.text()?.trim().orEmpty()

        val poster = if (isMovie && forcedBackdrop != null) {
            forcedBackdrop
        } else {
            document.selectFirst("img[src*=w1280]")
                ?.attr("src")
                ?.replace("w1280", "original")
                ?: document.selectFirst("article img")?.attr("src")
        }

        val plot = document.selectFirst("div.text-subs.md\\:text-lg")
            ?.text()
            ?.trim()
            ?: document.selectFirst("div.text-subs")?.text()?.trim()

        val year = document.select("span")
            .firstOrNull { it.text().matches(Regex("\\d{4}")) }
            ?.text()
            ?.toIntOrNull()

        val duration = document.select("span")
            .firstOrNull { it.text().contains("minutos") }
            ?.let { Regex("(\\d+)").find(it.text())?.groupValues?.get(1)?.toIntOrNull() }

        val score = document.selectFirst("span.text-main")?.text()?.toDoubleOrNull()

        return if (isMovie) {
            newMovieLoadResponse(title, realUrl, TvType.Movie, realUrl) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = duration
                this.score = Score.from10(score)
            }
        } else {
            val episodes = document.select("div[id^=season-]").flatMap { seasonEl ->
                val seasonNumber = seasonEl.attr("id")
                    .removePrefix("season-")
                    .toIntOrNull()
                    ?.plus(1) ?: 1

                seasonEl.select("article").map { ep ->
                    val epNum = Regex("E(\\d+)")
                        .find(ep.selectFirst("span.text-main")?.text().orEmpty())
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 1

                    newEpisode(ep.selectFirst("a")?.attr("href").orEmpty()) {
                        name = ep.selectFirst("h2")?.text()
                        season = seasonNumber
                        episode = epNum
                        description = ep.selectFirst("div.line-clamp-2.text-xs")?.text()?.trim()
                        posterUrl = ep.selectFirst("img")?.attr("src")
                    }
                }
            }

            newTvSeriesLoadResponse(title, realUrl, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = duration
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return OverFlixExtractor().extractLinks(data, subtitleCallback, callback)
    }
}
