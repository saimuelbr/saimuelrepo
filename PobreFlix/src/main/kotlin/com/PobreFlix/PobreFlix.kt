package com.PobreFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Calendar
import java.util.EnumSet

class PobreFlix : MainAPI() {

    companion object {
        private const val BASE_URL = "https://www.pobreflixtv.beer"
        private val CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR)
    }

    override var name = "PobreFlix"
    override var lang = "pt-br"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var mainUrl = BASE_URL

    private fun normalizeUrl(url: String): String {
        val uri = URI(url)
        return buildString {
            append(mainUrl)
            append(uri.path ?: "")
            uri.query?.let { append("?").append(it) }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/genero/filmes-de-$CURRENT_YEAR-online-65/" to "Filmes - $CURRENT_YEAR",
        "$mainUrl/genero/series-de-$CURRENT_YEAR-online-82/" to "Séries - $CURRENT_YEAR",
        "$mainUrl/genero/filmes-de-acao-online-3/" to "Filmes - Ação",
        "$mainUrl/genero/series-de-acao-online-22/" to "Séries - Ação",
        "$mainUrl/genero/filmes-de-animacao-online-1/" to "Filmes - Animação",
        "$mainUrl/genero/series-de-animacao-online-20/" to "Séries - Animação",
        "$mainUrl/genero/filmes-de-comedia-online-4/" to "Filmes - Comédia",
        "$mainUrl/genero/series-de-comedia-online-23/" to "Séries - Comédia",
        "$mainUrl/genero/filmes-de-crime-online-5/" to "Filmes - Crime",
        "$mainUrl/genero/series-de-crime-online-24/" to "Séries - Crime",
        "$mainUrl/genero/filmes-de-drama-online-7/" to "Filmes - Drama",
        "$mainUrl/genero/series-de-drama-online-26/" to "Séries - Drama",
        "$mainUrl/genero/filmes-de-ficcao-cientifica-online-11/" to "Filmes - Ficção",
        "$mainUrl/genero/series-de-ficcao-cientifica-online-30/" to "Séries - Ficção",
        "$mainUrl/genero/filmes-de-guerra-online-12/" to "Filmes - Guerra",
        "$mainUrl/genero/series-de-guerra-online-31/" to "Séries - Guerra",
        "$mainUrl/genero/filmes-de-misterio-online-14/" to "Filmes - Mistério",
        "$mainUrl/genero/series-de-misterio-online-32/" to "Séries - Mistério"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url =
            if (page <= 1) request.data
            else "${request.data.removeSuffix("/")}/?page=$page/"

        val items = app.get(url).document
            .select("article.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/pesquisar/?p=${query.replace(" ", "+")}"
        return app.get(url).document
            .select("article.item")
            .mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("div.caption h3")?.text()?.trim() ?: return null
        val link = selectFirst("a")?.attr("href") ?: return null

        val poster =
            selectFirst("div.vb_image_container")?.attr("data-background-src")
                ?: Regex("""url\(['"]?(.*?)['"]?\)""")
                    .find(selectFirst("div.vb_image_container")?.attr("style").orEmpty())
                    ?.groupValues?.get(1)

        val audio = selectFirst("div.capa-audio")?.text().orEmpty()
        val quality = getQualityFromString(selectFirst("div.capa-quali")?.text())

        return newAnimeSearchResponse(title, link, TvType.Movie) {
            posterUrl = poster?.replace("w185", "original")
            this.quality = quality
            dubStatus =
                if (audio.contains("DUB", true))
                    EnumSet.of(DubStatus.Dubbed)
                else
                    EnumSet.of(DubStatus.Subbed)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val isSeries = document.select("span.escolha_span").isNotEmpty()

        val title = document.selectFirst("h1.ipsType_pageTitle span.titulo")
            ?.ownText()?.trim() ?: "Sem título"

        val plot = document.selectFirst("div.sinopse")?.apply {
            select("span#myBtn, b").remove()
        }?.text()?.replace("...", "")?.trim()

        val duration = document.select("div.infos span")
            .firstOrNull { it.text().contains("min") }
            ?.text()?.replace("min", "")?.trim()?.toIntOrNull()

        val score = document.selectFirst("div.infos span.imdb")
            ?.text()?.replace("/10", "")?.trim()?.toDoubleOrNull()

        val year = document.select("div.infos span:nth-child(2)")
            .text().toIntOrNull()

        val actors = document.select("div.extrainfo span")
            .firstOrNull { it.html().contains("<b>Elenco:</b>") }
            ?.text()
            ?.replace("Elenco:", "")
            ?.split(",")
            ?.map {
            Pair(Actor(it.trim(), null), null as String?)
    }


        val tags = document.select("span.gen a").map { it.text() }
        val poster = extractPoster(document, url, isSeries)

        return if (isSeries) {
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                loadEpisodes(document, url)
            ) {
                posterUrl = poster
                this.plot = plot
                this.duration = duration
                this.tags = tags
                this.year = year
                this.score = Score.from10(score)
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, "movie|$url") {
                posterUrl = poster
                this.plot = plot
                this.duration = duration
                this.tags = tags
                this.year = year
                this.score = Score.from10(score)
                addActors(actors)
            }
        }
    }

    private suspend fun extractPoster(
        document: Document,
        url: String,
        isSeries: Boolean
    ): String? {

        val playerUrl =
            if (isSeries)
                document.selectFirst("div.listagem li a")?.attr("href")
            else
                if (url.contains("?")) "$url&area=online" else "$url/?area=online"

        playerUrl?.let {
            val style = app.get(it).document
                .selectFirst("div#video_embed")
                ?.attr("style")

            Regex("""url\((.*?)\)""")
                .find(style.orEmpty())
                ?.groupValues?.get(1)
                ?.replace(Regex("['\"]"), "")
                ?.replace("w1280", "original")
                ?.let { return it }
        }

        return document.selectFirst("div.vb_image_container")
            ?.attr("data-background-src")
            ?.replace("w185", "original")
            ?.let { fixUrl(it) }
    }

    private suspend fun loadEpisodes(
        document: Document,
        url: String
    ): List<Episode> {

        val seasons = mutableSetOf<Int>()

        document.select("script").forEach {
            if (it.data().contains("DOMContentLoaded")) {
                Regex("""<li onclick='load\((\d+)\);'>""")
                    .findAll(it.data())
                    .forEach { m ->
                        m.groupValues[1].toIntOrNull()?.let(seasons::add)
                    }
            }
        }

        return seasons.flatMap { season ->
            val seasonDoc = app.get(normalizeUrl("$url?temporada=$season")).document
            seasonDoc.select("div.listagem li").mapNotNull { ep ->
                val href = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val epId = ep.attr("data-id").replaceFirst(season.toString(), "")
                newEpisode("series|$href") {
                    this.season = season
                    this.episode = epId.toIntOrNull()
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean =
        PobreFlixExtractor.getLinks(data, subtitleCallback, callback)
}
