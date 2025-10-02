package com.Streamberry

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Element

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

    private val searchConcurrency = Semaphore(6)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}page/$page/"
        val document = app.get(url).document ?: throw ErrorLoadingException("Falha ao carregar página principal")
        val items = document.select("div#archive-content article.item")
        val home = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {
        val url = "$mainUrl?s=${query.replace(" ", "+")}"
        val document = app.get(url).document ?: return@coroutineScope emptyList()
        val articles = document.select("div.result-item article")

        val deferred = articles.map { article ->
            async {
                searchConcurrency.withPermit {
                    val link = article.selectFirst(".image a")?.attr("href") ?: return@withPermit null
                    val detailDoc = runCatching { app.get(link).document }.getOrNull()
                    val poster = detailDoc?.selectFirst(".poster img[itemprop=image]")?.let { resolveImageSrc(it, detailDoc) }
                        ?: article.selectFirst(".thumbnail img")?.let { resolveImageSrc(it, article.ownerDocument() ?: return@withPermit null) }
                    Pair(article, poster?.let { fixUrl(it) })
                }
            }
        }
        deferred.awaitAll().mapNotNull { it?.let { (article, posterAbs) -> article.toSearchResultSearch(posterAbs) } }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document ?: throw ErrorLoadingException("Falha ao carregar página de detalhe")
        val isMovie = url.contains("/filmes/")
        val isSerie = url.contains("/series/")

        val headerData = document.selectFirst(".sheader")
        val title = headerData?.selectFirst(".data h1")?.text()?.trim() ?: "Sem título"

        val poster = headerData?.selectFirst(".poster img")?.let { resolveImageSrc(it, document) }?.let { fixUrl(it) }
            ?: document.selectFirst(".sheader .poster noscript img")?.attr("src")?.let { fixUrl(it) }

        val plot = document.selectFirst("#info .wp-content p")?.text()?.trim()
        val rating = document.extractRating()
        val genres = document.select(".sgeneros a").map { it.text().trim() }
        val year = document.selectFirst(".extra .date")?.text()?.takeLast(4)?.toIntOrNull()

        val movieText = document.select("div.extra span.runtime").text().trim()
        val duration = Regex("(\\d+)").find(movieText)?.groupValues?.get(1)?.toIntOrNull()

        val tags = genres
        val posterList = document.select("#dt_galery .g-item a").map { it.attr("href") }
        val imdbId = document.selectFirst(".meta .rating")?.text()?.takeIf { it.contains("IMDb") }

        if (isMovie) {
            val actors = document.select("#cast .persons .person[itemprop=actor]").mapNotNull { actorEl ->
                val name = actorEl.selectFirst(".name a")?.text()?.trim() ?: return@mapNotNull null
                val img = actorEl.selectFirst(".img img")
                    ?.let { resolveImageSrc(it, actorEl.ownerDocument() ?: document) }
                    ?.let { fixUrl(it) }
                Actor(name, img)
            }

            val sources = mutableListOf<String>()
            val dubladoRow = document.select(".fix-table tbody tr").firstOrNull { row ->
                row.selectFirst("td:nth-child(3)")?.text()?.contains("Dublado", ignoreCase = true) == true
            }
            dubladoRow?.selectFirst("a")?.attr("href")?.takeIf { it.isNotBlank() }?.let { sources.add(it) }

            return newMovieLoadResponse(title, url, TvType.Movie, sources) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.rating = rating
                this.tags = tags
                this.duration = duration
                this.backgroundPosterUrl = posterList.firstOrNull()
                addActors(actors)
                addImdbId(imdbId)
            }
        } else if (isSerie) {
            val episodes = document.select("#seasons .se-c ul.episodios li").map { epEl ->
                val numerando = epEl.selectFirst(".numerando")?.text()?.split("-")?.map { it.trim() } ?: listOf()
                val (seasonNum, epNum) = when (numerando.size) {
                    1 -> Pair(1, numerando.getOrNull(0)?.toIntOrNull() ?: 1)
                    else -> Pair(numerando.getOrNull(0)?.toIntOrNull() ?: 1, numerando.getOrNull(1)?.toIntOrNull() ?: 1)
                }
                val epTitle = epEl.selectFirst(".episodiotitle a")?.text()?.trim() ?: "Episódio $epNum"
                val epUrl = epEl.selectFirst(".episodiotitle a")?.attr("href") ?: ""

                val posterImgEl = epEl.selectFirst(".imagen img")
                val epPoster = posterImgEl?.let { resolveImageSrc(it, epEl.ownerDocument() ?: document) }
                    ?: epEl.selectFirst(".imagen noscript img")?.attr("src")
                val finalPoster = epPoster?.let { fixUrl(it) }

                newEpisode(epUrl) {
                    this.name = epTitle
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = finalPoster
                }
            }

            val actors = document.select("#cast .persons .person[itemprop=actor]").mapNotNull { actorEl ->
                val name = actorEl.selectFirst(".name a")?.text()?.trim() ?: return@mapNotNull null
                val img = actorEl.selectFirst(".img img")
                    ?.let { resolveImageSrc(it, actorEl.ownerDocument() ?: document) }
                    ?.let { fixUrl(it) }
                Actor(name, img)
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.rating = rating
                this.tags = tags
                this.duration = duration
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
        return try {
            val cleanData = normalizeDataString(data)
            if (cleanData.contains("/links/")) {
                val finalUrl = resolveRedirectFinalUrl(cleanData)
                FileMoon().getUrl(finalUrl, mainUrl, subtitleCallback, callback)
                true
            } else {
                val document = app.get(cleanData).document ?: return false
                val filemoonLink = document.select(".fix-table tr")
                    .firstOrNull { tr -> tr.selectFirst("img[src*='filemoon.to']") != null }
                    ?.selectFirst("a")?.attr("href")
                if (!filemoonLink.isNullOrBlank()) {
                    val finalUrl = resolveRedirectFinalUrl(filemoonLink)
                    FileMoon().getUrl(finalUrl, mainUrl, subtitleCallback, callback)
                    true
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizeDataString(data: String): String {
        if (data.startsWith("[") && data.endsWith("]")) {
            return data.removePrefix("[").removeSuffix("]").split(",").firstOrNull()?.trim()?.removeSurrounding("\"") ?: data
        }
        return data
    }

    private fun resolveImageSrc(imgEl: Element, doc: org.jsoup.nodes.Document): String? {
        val src = imgEl.attr("src")?.takeIf { it.isNotBlank() }
        if (!src.isNullOrBlank() && !src.startsWith("data:image/svg+xml")) return src
        val lazy = imgEl.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
        if (!lazy.isNullOrBlank() && !lazy.startsWith("data:image/svg+xml")) return lazy
        val noscript = imgEl.parent()?.selectFirst("noscript img")?.attr("src") ?: doc.selectFirst("noscript img")?.attr("src")
        return noscript?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveRedirectFinalUrl(initial: String): String {
        val first = runCatching { app.get(initial, allowRedirects = false) }.getOrNull()
        val loc1 = first?.headers?.get("location")
        if (!loc1.isNullOrBlank()) {
            val second = runCatching { app.get(loc1, allowRedirects = false) }.getOrNull()
            val loc2 = second?.headers?.get("location")
            return when {
                !loc1.isNullOrBlank() && loc1.contains("filemoon.to") -> loc1
                !loc2.isNullOrBlank() -> loc2
                !loc1.isNullOrBlank() -> loc1
                else -> initial
            }
        }
        return initial
    }

    private fun Element.extractRating(): Int? {
    val imdb = select("div.custom_fields")
        .firstOrNull { it.select("b.variante").text().contains("IMDb", ignoreCase = true) }
        ?.select("span.valor strong")?.text()?.trim()

    val tmdb = select("div.custom_fields")
        .firstOrNull { it.select("b.variante").text().contains("TMDb", ignoreCase = true) }
        ?.select("span.valor strong")?.text()?.trim()

    val ratingText = imdb ?: tmdb

    return ratingText?.toRatingInt()
}

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("h3 a")?.text()?.trim() ?: this.selectFirst("img")?.attr("alt")?.trim() ?: return null

        val posterAbs = this.selectFirst(".poster img")
            ?.let { resolveImageSrc(it, ownerDocument() ?: return null) }
            ?.let { fixUrl(it) }
            ?: this.selectFirst(".poster noscript img")?.attr("src")?.let { fixUrl(it) }

        val year = this.selectFirst(".data span")?.text()?.takeLast(4)?.toIntOrNull()
        val type = if (this.hasClass("movies")) TvType.Movie else TvType.TvSeries

        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = posterAbs
            this.year = year
        }
    }

    private fun Element.toSearchResultSearch(posterAbs: String?): SearchResponse? {
        val link = this.selectFirst(".image a")?.attr("href") ?: return null
        val title = this.selectFirst(".details .title a")?.text()?.trim() ?: this.selectFirst("img")?.attr("alt")?.trim() ?: return null

        val fallbackPoster = this.selectFirst(".thumbnail img")
            ?.let { resolveImageSrc(it, ownerDocument() ?: return null) }
            ?.let { fixUrl(it) }
        val year = this.selectFirst(".meta .year")?.text()?.toIntOrNull()
        val isMovie = this.selectFirst(".image span.movies") != null
        val type = if (isMovie) TvType.Movie else TvType.TvSeries

        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = posterAbs ?: fallbackPoster
            this.year = year
        }
    }
}
