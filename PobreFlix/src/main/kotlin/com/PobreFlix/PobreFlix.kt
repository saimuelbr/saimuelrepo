package com.PobreFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI
import java.util.EnumSet


class PobreFlix : MainAPI() {

    override var name = "PobreFlix"
    override var lang = "pt-br"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var mainUrl = "https://tvredecanais.bid"

    private fun getUrl(url: String): String {
        val uri = URI(url)
        return "${mainUrl}${uri.path ?: ""}${if (uri.query != null) "?${uri.query}" else ""}"
    }

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/assistir/filmes-online-online-2/", "Filmes"),
        Pair("${mainUrl}/assistir/series-online-online-3/", "Séries"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(getUrl("${request.data}?page=${page}")).document

        val home = response.select("div.ipsBox div.vbItemImage")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val response = app.get("${mainUrl}/pesquisar/?p=${query.replace(" ", "+")}").document


        val home = response.select("div.ipsBox div.vbItemImage")
            .mapNotNull { it.toSearchResult() }

        return home
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val title = this.selectFirst("div.caption a")?.text()?.trim() ?: ""

        val link = this.selectFirst("div.caption a")?.attr("href") ?: ""
        val postUrl = this.selectFirst("div.vb_image_container")?.attr("data-background-src")
        val qual = getQualityFromString(
            this.selectFirst("div.TopLeft span.capa-info.capa-quali")?.text()
        )
        val dub = this.selectFirst("div.TopLeft span.capa-info.capa-audio")?.text() ?: ""

        return newAnimeSearchResponse(
            title, link, TvType.Movie
        ) {
            this.posterUrl = postUrl
            this.dubStatus =
                if (dub.contains("DUB")) EnumSet.of(DubStatus.Dubbed)
                else EnumSet.of(DubStatus.Subbed)
            quality = qual
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mapOf("User-Agent" to USER_AGENT)).document
        
        val title = document.selectFirst("h1.ipsType_pageTitle span.titulo")?.ownText()?.trim() ?: "Sem título"
        
        var poster: String? = null
        var videoEmbedDiv = document.selectFirst("div#video_embed")
        
        if (videoEmbedDiv != null) {
            val style = videoEmbedDiv.attr("style")
            val backgroundImageMatch = Regex("""background-image:url\(([^)]+)\)""").find(style)
            if (backgroundImageMatch != null) {
                poster = backgroundImageMatch.groupValues[1].let { fixUrl(it) }
            }
        } else {
            val onlineUrl = if (url.contains("?area=online")) url else "$url?area=online"
            try {
                val onlineDoc = app.get(onlineUrl, headers = mapOf("User-Agent" to USER_AGENT)).document
                videoEmbedDiv = onlineDoc.selectFirst("div#video_embed")
                if (videoEmbedDiv != null) {
                    val style = videoEmbedDiv.attr("style")
                    val backgroundImageMatch = Regex("""background-image:url\(([^)]+)\)""").find(style)
                    if (backgroundImageMatch != null) {
                        poster = backgroundImageMatch.groupValues[1].let { fixUrl(it) }
                    }
                }
            } catch (e: Exception) {
            }
        }
        
        if (poster == null) {
            poster = document.selectFirst("div.vb_image_container")?.attr("data-background-src")?.let { fixUrl(it) }
        }
        

        val description =
            document.selectFirst("div.sinopse")?.text()?.replace("Ler mais...", "")?.trim()
        val rating = document.selectFirst("div.infos span.imdb")?.text()?.replace("/10", "")?.trim()
            ?.toRatingInt()
        val year = document.select("div.infos span:nth-child(2)").text().toIntOrNull()
        val actors = document.select("div.extrainfo span").firstOrNull {
            it.html()
                .contains("<b>Elenco:</b>")
        }?.childNodes()?.filterNot { it.nodeName() == "b" }?.map { it.toString().trim() }
        val category = document.select("span.gen a").map {
            it.text()
        }

        if (document.select("span.escolha_span").isNotEmpty()) {
            val list = ArrayList<Int>()

            document.select("script").map { script ->
                if (script.data()
                        .contains(Regex("""document\.addEventListener\("DOMContentLoaded",\s*function\(\)"""))
                ) {
                    val regex = """<li onclick='load\((\d+)\);'>""".toRegex()
                    val matches = regex.findAll(script.data())

                    for (match in matches) {
                        val value = match.groups[1]?.value
                        if (value != null) {
                            list.add(value.toInt())
                        }
                    }
                }
            }

            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            for (season in list) {

                val seasonResponse = app.get(getUrl("${url}?temporada=${season}")).document

                val episodes = seasonResponse.select("div.listagem li")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->

                        val ep = episode.selectFirst("li")?.attr("data-id")
                            ?.replaceFirst(season.toString(), "")
                        val href = episode.selectFirst("a")?.attr("href")

                        episodeList.add(newEpisode(href) {
                            this.season = season
                            this.episode = ep?.toInt()
                        })
                    }
                }
            }
            return newTvSeriesLoadResponse(
                title, url, TvType.TvSeries, episodeList
            ) {
                posterUrl = poster
                this.plot = description
                this.tags = category
                this.rating = rating
                this.year = year
                addActors(actors)
            }

        } else {

            return newMovieLoadResponse(
                title, url, TvType.Movie, url
            ) {
                posterUrl = poster
                this.plot = description
                this.tags = category
                this.rating = rating
                this.year = year
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

        val document = app.get(getUrl("${data}?area=online")).document

        document.select("ul#baixar_menu li.ipsMenu_item > a").map { li ->

            val decodedUrl = extractUrlParam(li.attr("href")).decryptUrl()

            decodedUrl.takeIf { it.startsWith("http") }?.let { url ->
                loadExtractor(url, url, subtitleCallback, callback)
            }

        }

        return data.isEmpty()
    }

    fun extrairLink(input: String): String? {
        val regex = """window\.location\.href\s*=\s*"(.*?)";""".toRegex()
        val matchResult = regex.find(input)
        return matchResult?.groups?.get(1)?.value
    }

    fun extractUrlParam(href: String): String {
        val regex = Regex("""[?&]url=([^&]+)""")
        return regex.find(href)?.groupValues?.get(1) ?: ""
    }

    fun String?.decryptUrl(): String {
        if (this == null) return ""
        val decoded = base64Decode(this)
        return decoded.reversed()
    }
}