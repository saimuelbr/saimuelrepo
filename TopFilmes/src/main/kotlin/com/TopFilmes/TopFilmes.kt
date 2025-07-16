package com.TopFilmes

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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.util.*
import kotlinx.coroutines.delay

const val MAIN_URL = "https://www.topfilmes.biz"
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

class TopFilmes : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "TopFilmes"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/genero/acao" to "Ação",
        "/genero/animacao" to "Animação",
        "/genero/comedia" to "Comédia",
        "/genero/drama" to "Drama",
        "/genero/documentario" to "Documentário",
        "/genero/ficcao-cientifica" to "Ficção Científica",
        "/genero/fantasia" to "Fantasia",
        "/genero/policial" to "Policial",
        "/genero/misterio" to "Mistério",
        "/genero/romance" to "Romance",
        "/genero/suspense" to "Suspense",
        "/genero/terror" to "Terror"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}/p/$page"
        delay(3000)
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(fixUrl(url), headers = headers).document
        val filmes = document.select("div.filmes div.filme")
        val home = filmes.mapNotNull { it.toSearchResult() }
        val hasNext = document.select("ul.pagination li a[rel=next]").isNotEmpty()
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
        val link = if (this.tagName() == "a") {
            this.attr("href")
        } else {
            selectFirst("a")?.attr("href")
        }
        val title = selectFirst("div.title")?.text()?.trim() 
            ?: selectFirst("h2")?.text()?.trim()
            ?: selectFirst("h3")?.text()?.trim()
            ?: selectFirst("a")?.attr("title")?.trim()
            ?: selectFirst("img")?.attr("alt")?.trim()
            ?: if (this.tagName() == "a") this.attr("title")?.trim() else null
        val imgElement = selectFirst("img")
        val poster = imgElement?.let { img ->
            val dataSrc = img.attr("data-src")
            val src = img.attr("src")
            val dataOriginal = img.attr("data-original")
            val dataLlSrc = img.attr("data-ll-src")
            when {
                !dataSrc.isNullOrBlank() -> dataSrc
                !src.isNullOrBlank() -> src
                !dataOriginal.isNullOrBlank() -> dataOriginal
                !dataLlSrc.isNullOrBlank() -> dataLlSrc
                else -> null
            }
        }
        val year = selectFirst("div.year")?.text()?.toIntOrNull()
            ?: selectFirst("span.year")?.text()?.toIntOrNull()
            ?: selectFirst(".year")?.text()?.toIntOrNull()
        if (link == null || title == null) {
            return null
        }
        val cleanPoster = poster?.replace("_filter(blur)", "")?.let { fixUrl(it) }
        return newMovieSearchResponse(title, fixUrl(link), TvType.Movie) {
            this.posterUrl = cleanPoster
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/busca?q=${query.replace(" ", "+" )}"
        delay(3000)
        val headers = mapOf("User-Agent" to USER_AGENT)
        val document = app.get(fixUrl(url), headers = headers).document
        var resultados = document.select("div.filmes div.filme")
        if (resultados.isEmpty()) {
            resultados = document.select("div.filme")
        }
        if (resultados.isEmpty()) {
            resultados = document.select("div.card")
        }
        if (resultados.isEmpty()) {
            val links = document.select("a[href*/assistir/]")
            if (links.isNotEmpty()) {
                resultados = links
            }
        }
        return resultados.mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(fixUrl(url), headers = mapOf("User-Agent" to USER_AGENT)).document
        val title = document.selectFirst("div.infos h2")?.text()?.trim() ?: "Sem título"
        val poster = document.selectFirst("div.player img")?.attr("src")?.let { fixUrl(it) }
        val year = document.select("div.infos div.info").getOrNull(0)?.text()?.toIntOrNull()
        val duration = document.select("div.infos div.info").getOrNull(1)?.text()?.trim()
        val genre = document.select("div.infos div.info").getOrNull(2)?.text()?.trim()
        val plot = document.selectFirst("div.infos div.sinopse")?.text()?.trim()
        val imdbRating = document.selectFirst("div.infos div.imdb span")?.text()?.toFloatOrNull()
        val genres = mutableListOf<String>()
        if (!genre.isNullOrBlank()) {
            genres.add(genre)
        }
        if (!duration.isNullOrBlank()) {
            genres.add("Duração: $duration")
        }
        val sources = getMovieSources(document, url)
        return newMovieLoadResponse(title, url, TvType.Movie, sources) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = genres
        }
    }

    private fun getMovieSources(document: org.jsoup.nodes.Document, url: String): List<String> {
        val playerLinks = document.select("div.links_dub a")
        if (playerLinks.isEmpty()) {
            val alternativeLinks = document.select("a[href*='player']")
        }
        val sources = playerLinks.mapNotNull { link ->
            val href = link.attr("href")
            val text = link.text().trim()
            if (href.isNotEmpty()) {
                val finalHref = if (href.startsWith("//")) "https:$href" else href
                finalHref
            } else {
                null
            }
        }
        return sources
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val realData = when {
                data.startsWith("[") && data.endsWith("]") -> {
                    data.removePrefix("[").removeSuffix("]").split(",").first().trim().removeSurrounding("\"")
                }
                else -> data
            }
            val playerUrl = if (realData.contains("player=")) {
                realData.replace(Regex("player=\\d+"), "player=1")
            } else {
                realData
            }
            val finalPlayerUrl = fixUrl(playerUrl)
            val document = app.get(finalPlayerUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
            val videoWrapper = document.selectFirst("div.plyr__video-wrapper")
            if (videoWrapper != null) {
                val videoElement = videoWrapper.selectFirst("video#player")
                val videoSrc = videoElement?.attr("src")
                if (!videoSrc.isNullOrBlank()) {
                    val finalVideoUrl = if (videoSrc.startsWith("//")) "https:$videoSrc" else videoSrc
                    callback(
                        newExtractorLink(
                            name,
                            "TopFilmes Video",
                            finalVideoUrl,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                        }
                    )
                    return true
                }
                val sourceElement = videoWrapper.selectFirst("source")
                val sourceSrc = sourceElement?.attr("src")
                if (!sourceSrc.isNullOrBlank()) {
                    val finalSourceUrl = if (sourceSrc.startsWith("//")) "https:$sourceSrc" else sourceSrc
                    callback(
                        newExtractorLink(
                            name,
                            "TopFilmes Video",
                            finalSourceUrl,
                            INFER_TYPE
                        ) {
                            this.referer = mainUrl
                        }
                    )
                    return true
                }
            }
            val anyVideo = document.selectFirst("video")
            val anyVideoSrc = anyVideo?.attr("src")
            if (!anyVideoSrc.isNullOrBlank()) {
                val finalAnyVideoUrl = if (anyVideoSrc.startsWith("//")) "https:$anyVideoSrc" else anyVideoSrc
                callback(
                    newExtractorLink(
                        name,
                        "TopFilmes Video",
                        finalAnyVideoUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                    }
                )
                return true
            }
            val allSourceTags = document.select("source")
            val validSource = allSourceTags.firstOrNull { source ->
                val src = source.attr("src")
                val type = source.attr("type")
                !src.isNullOrBlank() && type == "video/mp4"
            }
            if (validSource != null) {
                val sourceSrc = validSource.attr("src")
                val finalSourceUrl = if (sourceSrc.startsWith("//")) "https:$sourceSrc" else sourceSrc
                callback(
                    newExtractorLink(
                        name,
                        "TopFilmes Video",
                        finalSourceUrl,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                    }
                )
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
} 