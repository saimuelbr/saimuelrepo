package com.Vizer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class Vizer : MainAPI() {
    override var name = "Vizer"
    override var lang = "pt-br"
    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPageDelay = 300L
    override var sequentialMainPageScrollDelay = 300L

    override var mainUrl = "https://novizer.com/"

    private fun getUrl(url: String): String {
        val uri = URI(url)
        return "${mainUrl}${uri.path ?: ""}${if (uri.query != null) "?${uri.query}" else ""}"
    }

    private fun getAPIUrl(url: String): String {

        val paramMap = mapOf(
            "search" to "",
            "saga" to "0",
            "categoryFilterYearMin" to "1890",
            "categoryFilterYearMax" to "2024",
            "categoryFilterOrderBy" to "year",
            "categoryFilterOrderWay" to "desc",
        )

        val stringParamMap: Map<String, String> = paramMap.mapValues { it.value.toString() }

        return addQueryArg(
            url,
            stringParamMap
        )
    }

    private fun addQueryArg(url: String, params: Map<String, String> = emptyMap()): String {
        val uri = URI(url)
        val existingParams = mutableMapOf<String, String>()

        uri.rawQuery?.split("&")?.forEach { param ->
            val (key, value) = param.split("=")
            existingParams[key] = value
        }

        params.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                existingParams[key] =
                    URLEncoder.encode(value, "UTF-8")
            }
        }

        val queryString = existingParams.entries.joinToString("&") { (key, value) -> "$key=$value" }

        return "${mainUrl}${uri.path}?$queryString"
    }

    private fun getImgUrl(url: String, id: String): String {
        return if (url.contains("Movies")) {
            getUrl("/content/movies/posterPt/342/${id}.webp")
        } else {
            getUrl("/content/series/posterPt/342/${id}.webp")
        }
    }

    private fun getUri(type: String, url: String): String {
        return if (type.contains("Movies")) {
            getUrl("/filme/online/${url}")
        } else {
            getUrl("/serie/online/${url}")
        }
    }

    override val mainPage = mainPageOf(
        Pair(
            getAPIUrl("${mainUrl}/includes/ajax/ajaxPagination.php?categoriesListMovies=all"),
            "Filmes"
        ),
        Pair(
            getAPIUrl("${mainUrl}/includes/ajax/ajaxPagination.php?categoriesListSeries=all"),
            "Séries"
        ),
        Pair(
            getAPIUrl("${mainUrl}/includes/ajax/ajaxPagination.php?categoriesListSeries=all&anime=1"),
            "Animes"
        ),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeResponse = app.get(getUrl("${request.data}&page=${page - 1}")).text

        val mapper = jacksonObjectMapper()
        val json: DataPageJson = mapper.readValue(homeResponse)

        val home = json.list.map { item ->
            newMovieSearchResponse(
                item.title,
                getUri(request.data, item.url),
                if (request.data.contains("filme")) TvType.Movie else TvType.TvSeries,
            ) {
                this.posterUrl = getImgUrl(request.data, item.id)
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val soup = app.get("${mainUrl}/pesquisar/${query.replace(" ", "%20")}").document

        val home = soup.select("div.listItems a.gPoster").mapNotNull {
            it.toSearchResult()
        }

        return home
    }

    private fun Element.toSearchResult(): MovieSearchResponse? {
        val title = this.selectFirst("div.infos span")?.text()?.trim() ?: ""

        val link = this.selectFirst("a.gPoster")?.attr("href") ?: ""
        val postUrl = getUrl(this.selectFirst("a.gPoster picture img.img")?.attr("src") ?: "")

        return newMovieSearchResponse(
            title, getUrl("/${link}"), TvType.Movie
        ) {
            this.posterUrl = postUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(getUrl(url)).document

        val title = document.selectFirst("section.ai h2")?.ownText()?.trim() ?: ""
        val description = document.selectFirst("section.ai span.desc")?.text()?.trim()
        val rating = document.selectFirst("section.ai a.rating")?.text()?.replace("/10", "")?.trim()
            .toRatingInt()
        val stupidPosterImg = document.selectFirst("div.stupidPoster img")?.attr("src")
        val backgroundPoster = stupidPosterImg
            ?.replace("/posterPt/", "/background/")
            ?.replace("/342/", "/1280/")
            ?.let { if (it.startsWith("/")) "$mainUrl$it" else it }
            ?: fixUrlNull(stupidPosterImg)
        val year = document.select("section.ai div.year").text().toIntOrNull()
        val runtime = document.select("div.dur div.tm").text().runTime()
        val actors = document.select("section.ai a.personCard").map {
            val name = it.selectFirst("span")?.text().toString()
            val imgPath = it.selectFirst("picture.img img")?.attr("src")
            Actor(name, getUrl("$imgPath"))
        }

        if (document.select("div.selectorModal").isNotEmpty()) {

            val listSeasonsID = ArrayList<Pair<String, String>>()

            document.select("div.selectorModal div.seasons div.list div.item").map {

                val name = it.select("div.item").text()

                val id = it.select("div.item").attr("data-season-id")

                listSeasonsID.add(Pair(name, id))

            }

            if (listSeasonsID.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            listSeasonsID.forEachIndexed { index, season ->

                when {
                    index % 15 == 5 -> {
                        Thread.sleep(1000)
                    }

                    index % 15 == 10 -> {
                        Thread.sleep(2000)
                    }

                    index % 15 == 0 && index != 0 -> {
                        Thread.sleep(3000)
                    }
                }

                val nomeTemporada = extractNumber(season.first)
                val id = season.second

                val respostaTemporada = app.post(
                    getUrl("/includes/ajax/publicFunctions.php"),
                    referer = url,
                    data = mapOf("getEpisodes" to id)
                ).text

                val mapper = jacksonObjectMapper()
                val json: DataPlayerJson = mapper.readValue(respostaTemporada)

                json.list.forEach { episode ->
                    episodeList.add(newEpisode("${url}?episode=${episode.id}") {
                        this.season = nomeTemporada
                        this.episode = episode.name.toIntOrNull()
                        this.name = episode.title
                        this.posterUrl = getUrl("/content/series/episodes/185/${episode.id}.jpg")
                    })
                }
            }

            return newTvSeriesLoadResponse(
                title, url, TvType.TvSeries, episodeList
            ) {
                posterUrl = backgroundPoster
                this.plot = description
                this.duration = runtime.toIntOrNull()
                this.rating = rating
                this.year = year
                addActors(actors)
            }

        } else {

            val movieID = document.select("a.btn.click").attr("href")

            return newMovieLoadResponse(
                title, url, TvType.Movie, movieID.getMovieID()
            ) {
                posterUrl = backgroundPoster
                this.plot = description
                this.duration = runtime.toIntOrNull()
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

        if (data.isNotEmpty()) {

            val down = if (isUrl(data)) "2" else "1"

            val playerResponse = app.post(
                getUrl("/includes/ajax/publicFunctions.php"),
                referer = mainUrl,
                data = mapOf(
                    "downloadData" to down,
                    "id" to data.getEpisodeID(),
                )
            ).text

            val mapper = jacksonObjectMapper()
            val dataPlayer: Map<String, DataPlayerContent> = mapper.readValue(playerResponse)

            dataPlayer.forEach { (_, content) ->
                content.redirector.decryptKey().takeIf { it.isNotEmpty() }?.let { link ->
                    loadExtractor(link, link, subtitleCallback, callback)
                    content.sub?.let { sub ->
                        subtitleCallback(SubtitleFile("Português", sub))
                    }
                }
            }

        }

        return data.isEmpty()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DataContentJson(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("rating") val rating: String,
        @JsonProperty("runtime") val runtime: String?,
        @JsonProperty("name") val name: String = ""
    )

    data class DataPageJson(
        @JsonProperty("status") val status: String?,
        @JsonProperty("quantity") val quantity: Int?,
        @JsonProperty("maxQuantity") val maxQuantity: String?,
        @JsonProperty("list") @JsonDeserialize(converter = ListDeserializer::class) val list: List<DataContentJson> = listOf()
    )

    class ListDeserializer : StdConverter<Map<String, DataContentJson>, List<DataContentJson>>() {
        override fun convert(value: Map<String, DataContentJson>): List<DataContentJson> {
            return value.values.toList()
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ListItemEpisode(
        val id: String,
    )

    data class DataPlayerContent(
        @JsonProperty("id") val id: String,
        @JsonProperty("audio") val audio: String? = null,
        @JsonProperty("originalName") val originalName: String? = null,
        @JsonProperty("redirector") val redirector: String,
        @JsonProperty("sub") val sub: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DataEpisodeContent(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("rating") val rating: String,
        @JsonProperty("runtime") val runtime: String?,
        @JsonProperty("name") val name: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DataPlayerJson(
        @JsonProperty("status") val status: String?,
        @JsonProperty("list") @JsonDeserialize(converter = ListPlayerDeserializer::class) val list: List<DataEpisodeContent> = listOf()
    )

    class ListPlayerDeserializer :
        StdConverter<Map<String, DataEpisodeContent>, List<DataEpisodeContent>>() {
        override fun convert(value: Map<String, DataEpisodeContent>): List<DataEpisodeContent> {
            return value.values.toList()
        }
    }

    fun String?.runTime(): String {
        if (this == null) {
            return "0"
        }

        val (horas, minutos) = when {
            contains("h") -> split(",").map { it.trim() }
            else -> listOf("0h", this.trim())
        }

        val horasInt = horas.replace("h", "").toIntOrNull() ?: 0
        val minutosInt = minutos.replace("min", "").toIntOrNull() ?: 0

        return (horasInt * 60 + minutosInt).toString()
    }

    private fun extractNumber(text: String): Int? {
        val regex = """(\d+)""".toRegex()
        val matchResult = regex.find(text)
        return matchResult?.groups?.get(1)?.value?.toIntOrNull()
    }

    private fun extrairLink(input: String): String? {
        val regex = """window\.location\.href\s*=\s*"(.*?)";""".toRegex()
        val matchResult = regex.find(input)
        return matchResult?.groups?.get(1)?.value
    }

    private fun String?.getEpisodeID(): String {
        if (this == null) return ""
        val regex = """[?&]episode=([^&]+)""".toRegex()
        val matchResult = regex.find(this)
        return matchResult?.groups?.get(1)?.value?.trim() ?: this
    }

    private fun String?.getMovieID(): String {
        if (this == null) return ""
        val regex = """[?&]movie=([^&]+)""".toRegex()
        val matchResult = regex.find(this)
        return matchResult?.groups?.get(1)?.value?.trim() ?: this
    }

    private fun String?.decryptKey(): String {
        if (this == null) return ""
        var decoded = base64Decode(this.replace("redirect/", ""))
        decoded = decoded.trim()
        decoded = decoded.reversed()
        val last = decoded.takeLast(5).reversed()
        decoded = decoded.dropLast(5)
        return decoded + last
    }

    private fun isUrl(text: String): Boolean {
        val regex = """^(http(s)?://)[^\s]+?\.[^\s]+/?""".toRegex()
        return regex.matches(text)
    }

    private fun extractSrtUrl(url: String): String? {
        if (!url.contains(".srt")) return null

        val uri = URI(url)
        val query = uri.rawQuery ?: return null

        query.split("&").forEach { param ->
            val parts = param.split("=")
            if (parts.size == 2) {
                val value = parts[1]
                if (value.endsWith(".srt")) {
                    return value
                }
            }
        }

        return null
    }
} 