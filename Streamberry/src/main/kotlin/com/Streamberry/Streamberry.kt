package com.Streamberry

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
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
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ErrorLoadingException
import java.util.*
import com.lagradost.cloudstream3.extractors.FileMoon
import kotlinx.coroutines.delay

class Streamberry : MainAPI() {
    override var mainUrl = "https://streamberry.com.br/"
    override var name = "Streamberry"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "/filmes/" to "Filmes - Adicionados Recentemente",
        "/series/" to "Séries - Adicionados Recentemente"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document
        val items = document.select("div#archive-content article.item")
        val home = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        val items = document.select("div.result-item article")
        return items.mapNotNull { article ->
            val link = article.selectFirst(".image a")?.attr("href") ?: return@mapNotNull null
            val detailDoc = try { app.get(link).document } catch (e: Exception) { null }
            val posterDiv = detailDoc?.selectFirst(".poster")
            val detailPosterImg = posterDiv?.selectFirst("img[itemprop=image]")
            var detailPoster = detailPosterImg?.attr("src")
            if (detailPoster != null && detailPoster.startsWith("data:image/svg+xml")) {
                detailPoster = detailPosterImg?.attr("data-lazy-src")
            }
            if (detailPoster.isNullOrBlank() || detailPoster.startsWith("data:image/svg+xml")) {
                val noscriptImg = posterDiv?.selectFirst("noscript")?.selectFirst("img")
                val noscriptSrc = noscriptImg?.attr("src")
                if (!noscriptSrc.isNullOrBlank()) {
                    detailPoster = noscriptSrc
                }
            }
            val posterAbs = detailPoster?.let { fixUrl(it) }
            article.toSearchResultSearch(posterAbs)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val isMovie = url.contains("/filmes/")
        val isSerie = url.contains("/series/")
        val title = document.selectFirst(".sheader .data h1")?.text()?.trim() ?: "Sem título"
        val posterImg = document.selectFirst(".sheader .poster img")
        var poster = posterImg?.attr("src")
        if (poster != null && poster.startsWith("data:image/svg+xml")) {
            poster = posterImg?.attr("data-lazy-src")
        }
        if (poster.isNullOrBlank() || poster.startsWith("data:image/svg+xml")) {
            val noscriptImg = document.selectFirst(".sheader .poster noscript")?.selectFirst("img")
            val noscriptSrc = noscriptImg?.attr("src")
            if (!noscriptSrc.isNullOrBlank()) {
                poster = noscriptSrc
            }
        }
        val plot = document.selectFirst("#info .wp-content p")?.text()?.trim()
        val genres = document.select(".sgeneros a").map { it.text().trim() }
        val year = document.selectFirst(".extra .date")?.text()?.takeLast(4)?.toIntOrNull()
        val duration = document.selectFirst(".extra .runtime")?.text()?.replace("Min.", "")?.trim()?.toIntOrNull()
        val tags = genres
        val posterList = document.select("#dt_galery .g-item a").map { it.attr("href") }
        val imdbId = document.selectFirst(".meta .rating")?.text()?.takeIf { it.contains("IMDb") }

        if (isMovie) {
            val actors = document.select("#cast .persons .person[itemprop=actor]").mapNotNull { actorEl ->
                val actorName = actorEl.selectFirst(".name a")?.text()?.trim()
                val imgEl = actorEl.selectFirst(".img img")
                var actorImg = imgEl?.attr("src")
                if (actorImg != null && actorImg.startsWith("data:image/svg+xml")) {
                    actorImg = imgEl?.attr("data-lazy-src")
                }
                if (actorImg.isNullOrBlank() || actorImg.startsWith("data:image/svg+xml")) {
                    val noscriptImg = actorEl.selectFirst(".img noscript")?.selectFirst("img")
                    val noscriptSrc = noscriptImg?.attr("src")
                    if (!noscriptSrc.isNullOrBlank()) {
                        actorImg = noscriptSrc
                    }
                }
                if (actorName != null) Actor(actorName, actorImg?.let { fixUrl(it) }) else null
            }
            val sources = mutableListOf<String>()
            
            val dubladoRow = document.select(".fix-table tbody tr")
                .firstOrNull { row ->
                    val languageElement = row.selectFirst("td:nth-child(3)")
                    languageElement?.text()?.contains("Dublado", ignoreCase = true) == true
                }
            
            if (dubladoRow != null) {
                val linkElement = dubladoRow.selectFirst("a")
                val link = linkElement?.attr("href")
                
                if (!link.isNullOrEmpty()) {
                    sources.add(link)
                }
            }
            return newMovieLoadResponse(title, url, TvType.Movie, sources) {
                this.posterUrl = poster?.let { fixUrl(it) }
                this.year = year
                this.plot = plot
                this.tags = tags
                this.backgroundPosterUrl = posterList.firstOrNull()
                addActors(actors)
                addImdbId(imdbId)
            }
        } else if (isSerie) {
            val episodes = document.select("#seasons .se-c ul.episodios li").map { epEl ->
                val seasonNum = epEl.selectFirst(".numerando")?.text()?.split("-")?.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
                val epNum = epEl.selectFirst(".numerando")?.text()?.split("-")?.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                val epTitle = epEl.selectFirst(".episodiotitle a")?.text()?.trim() ?: "Episódio $epNum"
                val epUrl = epEl.selectFirst(".episodiotitle a")?.attr("href") ?: ""
                val epPoster = epEl.selectFirst(".imagen img")?.let { img ->
                    img.attr("src").ifBlank { img.attr("data-lazy-src") }
                }
                newEpisode(epUrl) {
                    this.name = epTitle
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = epPoster
                }
            }
            val actors = document.select("#cast .persons .person[itemprop=actor]").mapNotNull { actorEl ->
                val actorName = actorEl.selectFirst(".name a")?.text()?.trim()
                val imgEl = actorEl.selectFirst(".img img")
                var actorImg = imgEl?.attr("src")
                if (actorImg != null && actorImg.startsWith("data:image/svg+xml")) {
                    actorImg = imgEl?.attr("data-lazy-src")
                }
                if (actorImg.isNullOrBlank() || actorImg.startsWith("data:image/svg+xml")) {
                    val noscriptImg = actorEl.selectFirst(".img noscript")?.selectFirst("img")
                    val noscriptSrc = noscriptImg?.attr("src")
                    if (!noscriptSrc.isNullOrBlank()) {
                        actorImg = noscriptSrc
                    }
                }
                if (actorName != null) Actor(actorName, actorImg?.let { fixUrl(it) }) else null
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.let { fixUrl(it) }
                this.year = year
                this.plot = plot
                this.tags = tags
                this.backgroundPosterUrl = posterList.firstOrNull()
                addActors(actors)
                addImdbId(imdbId)
            }
        } else {
            throw ErrorLoadingException("Tipo de conteúdo não reconhecido")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val cleanData = if (data.startsWith("[") && data.endsWith("]")) {
                data.removePrefix("[").removeSuffix("]")
                    .split(",")
                    .firstOrNull()
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?: data
            } else {
                data
            }
            
            if (cleanData.contains("/links/")) {
                val redirectResp = app.get(cleanData, allowRedirects = false)
                val location = redirectResp.headers["location"] ?: cleanData
                val finalUrl = if (location.contains("filemoon.to")) location else {
                    val resp2 = app.get(location, allowRedirects = false)
                    resp2.headers["location"] ?: location
                }
                FileMoon().getUrl(finalUrl, mainUrl, subtitleCallback, callback)
                return true
            } else {
                val document = app.get(cleanData).document
                val filemoonLink = document.select(".fix-table tr")
                    .firstOrNull { tr -> tr.selectFirst("img[src*='filemoon.to']") != null }
                    ?.selectFirst("a")?.attr("href")
                if (filemoonLink != null) {
                    val redirectResp = app.get(filemoonLink, allowRedirects = false)
                    val location = redirectResp.headers["location"] ?: filemoonLink
                    val finalUrl = if (location.contains("filemoon.to")) location else {
                        val resp2 = app.get(location, allowRedirects = false)
                        resp2.headers["location"] ?: location
                    }
                    FileMoon().getUrl(finalUrl, mainUrl, subtitleCallback, callback)
                    return true
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("h3 a")?.text()?.trim() ?: this.selectFirst("img")?.attr("alt")?.trim() ?: return null
        val posterDiv = this.selectFirst(".poster")
        val posterImg = posterDiv?.selectFirst("img")
        var posterSrc = posterImg?.attr("src")
        if (posterSrc != null && posterSrc.startsWith("data:image/svg+xml")) {
            posterSrc = posterImg?.attr("data-lazy-src")
        }
        if (posterSrc.isNullOrBlank() || posterSrc.startsWith("data:image/svg+xml")) {
            val noscriptImg = posterDiv?.selectFirst("noscript")?.selectFirst("img")
            val noscriptSrc = noscriptImg?.attr("src")
            if (!noscriptSrc.isNullOrBlank()) {
                posterSrc = noscriptSrc
            }
        }
        val posterAbs = posterSrc?.let { fixUrl(it) }
        val year = this.selectFirst(".data span")?.text()?.takeLast(4)?.toIntOrNull()
        val isMovie = this.hasClass("movies")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries
        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = posterAbs
            this.year = year
        }
    }

    private fun Element.toSearchResultSearch(posterAbs: String?): SearchResponse? {
        val link = this.selectFirst(".image a")?.attr("href") ?: return null
        val title = this.selectFirst(".details .title a")?.text()?.trim() ?: this.selectFirst("img")?.attr("alt")?.trim() ?: return null
        val thumbDiv = this.selectFirst(".thumbnail")
        val posterImg = thumbDiv?.selectFirst("img")
        var posterSrc = posterImg?.attr("src")
        if (posterSrc != null && posterSrc.startsWith("data:image/svg+xml")) {
            posterSrc = posterImg?.attr("data-lazy-src")
        }
        if (posterSrc.isNullOrBlank() || posterSrc.startsWith("data:image/svg+xml")) {
            val noscriptImg = thumbDiv?.selectFirst("noscript")?.selectFirst("img")
            val noscriptSrc = noscriptImg?.attr("src")
            if (!noscriptSrc.isNullOrBlank()) {
                posterSrc = noscriptSrc
            }
        }
        val fallbackPoster = posterSrc?.let { fixUrl(it) }
        val year = this.selectFirst(".meta .year")?.text()?.toIntOrNull()
        val isMovie = this.selectFirst(".image span.movies") != null
        val type = if (isMovie) TvType.Movie else TvType.TvSeries
        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = posterAbs ?: fallbackPoster
            this.year = year
        }
    }
} 