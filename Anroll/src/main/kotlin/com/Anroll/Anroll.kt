package com.Anroll

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class Anroll : MainAPI() {
    override var mainUrl = "https://www.anroll.net"
    override var name = "Anroll"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    companion object {
        private const val episodeUrl = "https://apiv3-prd.anroll.net"
        private const val posterUrl = "https://static.anroll.net"
        private const val videoUrl = "https://cdn-zenitsu-2-gamabunta.b-cdn.net"
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val animeUrl = "$episodeUrl/animes?page=$page&gen=todos&alpha=az"
        val animeRes = tryParseJson<ApiResponse>(app.get(animeUrl).text)

        val animeItems = animeRes?.data?.mapNotNull { item ->
            val title = item.titulo?.trim().orEmpty()
            val generateId = item.generate_id?.trim().orEmpty()
            val slug = item.slug_serie?.trim().orEmpty()
            if (title.isEmpty() || generateId.isEmpty() || slug.isEmpty()) return@mapNotNull null

            val href = "$mainUrl/a/$generateId"
            val poster = "$posterUrl/images/animes/capas/$slug.jpg"

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        } ?: emptyList()

        val filmesUrl = "https://www.anroll.net/_next/data/RWySOXkJe1_j6zQD6H8T_/filmes.json"
        val filmesRes = tryParseJson<FilmesResponse>(app.get(filmesUrl).text)

        val filmeItems = filmesRes?.pageProps?.data?.data_movies?.mapNotNull { item ->
            val title = item.nome_filme?.trim().orEmpty()
            val generateId = item.generate_id?.trim().orEmpty()
            val slug = item.slug_filme?.trim().orEmpty()
            if (title.isEmpty() || generateId.isEmpty() || slug.isEmpty()) return@mapNotNull null

            val href = "$mainUrl/f/$generateId"
            val poster = "$posterUrl/images/filmes/capas/$slug.jpg"

            newAnimeSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = poster
            }
        } ?: emptyList()

        val hasNext = animeRes?.meta?.hasNextPage == true
        return newHomePageResponse(
            listOf(
                HomePageList("Filmes - Todos", filmeItems),
                HomePageList("Animes - Todos", animeItems)
            ),
            hasNext
        )
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val title = this.selectFirst("h1")?.text()?.trim() ?: ""
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        val epNum = this.selectFirst("span.sc-f5d5b250-3.fsTgnD b")?.text()?.toIntOrNull()
        val isDub = this.selectFirst("div.sc-9dbd1f1d-5.efznig")?.text() == "DUB"
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(isDub, epNum)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val res =
            app.get("https://api-search.anroll.net/data?q=${query}").parsedSafe<SearchApiResponse>()
        return res?.data?.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val genId = item.gen_id ?: return@mapNotNull null
            val slug = item.slug ?: return@mapNotNull null
            val type = if (item.type == "movie") TvType.AnimeMovie else TvType.Anime
            val href = if (item.type == "movie") "$mainUrl/f/$genId" else "$mainUrl/a/$genId"
            val poster = if (item.type == "movie") {
                "$posterUrl/images/filmes/capas/$slug.jpg"
            } else {
                "$posterUrl/images/animes/capas/$slug.jpg"
            }

            newAnimeSearchResponse(title, href, type) {
                this.posterUrl = poster
                this.year = item.year?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {

        val fixUrl = getProperAnimeLink(url) ?: throw ErrorLoadingException()

        val type = if (fixUrl.contains("/a/")) TvType.Anime else TvType.AnimeMovie

        return if (type == TvType.AnimeMovie) {
            val genId = fixUrl.substringAfterLast("/")
            val movieApiUrl =
                "https://www.anroll.net/_next/data/RWySOXkJe1_j6zQD6H8T_/f/$genId.json?movie=$genId"
            val movieRes = tryParseJson<MovieApiResponse>(app.get(movieApiUrl).text)
            val movieData = movieRes?.pageProps?.data?.data_movie

            val slug = movieData?.slug_filme?.trim().orEmpty()

            newMovieLoadResponse(movieData?.nome_original ?: "", url, type, url) {
                posterUrl = "${Companion.posterUrl}/images/filmes/capas/$slug.jpg"
                movieData?.ano.also { this.year = it?.toIntOrNull() }
                movieData?.sinopse_filme.also { this.plot = it }
                this.dataUrl = url
            }

        } else {

            val document = app.get(fixUrl).document

            val article = document.selectFirst("article.animedetails") ?: return null
            val title = article.selectFirst("h2")?.text() ?: return null

            val bgPosterFromStyle =
                document.selectFirst("div[style*=static.anroll.net][style*=images/animes/screens/]")
                    ?.attr("style")
                    ?.let { style ->
                        Regex("""https?://static\.anroll\.net/[^"')\\s]+\.jpg""")
                            .find(style)?.value
                    }
                    ?: run {
                        val html = document.html()
                        Regex("""https?://static\.anroll\.net/[^"')\\s]+\.jpg""")
                            .find(html)?.value
                    }
            var poster = bgPosterFromStyle
            if (poster.isNullOrBlank()) {
                poster = fixUrlNull(document.select("section.animecontent img").attr("src"))
            }

            val tags = article.select("div#generos a").map { it.text() }
            val year = article.selectFirst("div.dfuefM")?.nextElementSibling()?.text()
                ?.toIntOrNull()
            val description = document.select("div.sinopse").text().trim()

            val episodes = mutableListOf<Episode>()
            val baseId = fixUrl.substringAfterLast("/")
            val firstText = app.get("$episodeUrl/animes/$baseId/episodes?page=1&order=asc").text
            val first = tryParseJson<EpisodesResponse>(firstText)
            val totalPages = first?.meta?.totalOfPages ?: 1

            fun map(list: List<DataEpisode>?): List<Episode> {
                return list?.map { ep ->
                    val episodeNum = ep.n_episodio?.toIntOrNull()
                    newEpisode(
                        Load(
                            ep.anime?.get("slug_serie"),
                            ep.n_episodio,
                            "animes"
                        ).toJson()
                    ) {
                        this.episode = episodeNum
                        this.name = ep.titulo_episodio
                        this.description = ep.sinopse_episodio
                        this.posterUrl =
                            ep.anime?.get("slug_serie")?.fixImageUrl(Image.Episode, episodeNum)
                    }
                } ?: emptyList()
            }

            episodes.addAll(map(first?.data))

            for (p in 2..totalPages) {
                val txt = app.get("$episodeUrl/animes/$baseId/episodes?page=$p&order=asc").text
                val pr = tryParseJson<EpisodesResponse>(txt)
                episodes.addAll(map(pr?.data))
            }

            val backgroundPoster = episodes.firstOrNull()?.posterUrl ?: poster

            newAnimeLoadResponse(title, url, type) {
                posterUrl = backgroundPoster
                this.year = year
                addEpisodes(DubStatus.Subbed, episodes)
                plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("http")) {
            val genId = data.substringAfterLast("/")
            val movieApiUrl =
                "https://www.anroll.net/_next/data/RWySOXkJe1_j6zQD6H8T_/f/$genId.json?movie=$genId"
            val movieRes = tryParseJson<MovieApiResponse>(app.get(movieApiUrl).text)
            val movieData = movieRes?.pageProps?.data?.data_movie
            val slugFilme = movieData?.slug_filme ?: return false

            val streamUrl = "$videoUrl/cf/hls/movies/$slugFilme/movie.mp4/media-1/stream.m3u8"

            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = streamUrl,
                    type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = getHeaders()
                }
            )
            return true
        }

        val load = tryParseJson<Load>(data)
        val streamUrl =
            "$videoUrl/cf/hls/${load?.type}/${load?.slug_serie}/${load?.n_episodio}.mp4/media-1/stream.m3u8"

        callback(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = streamUrl,
                type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.headers = getHeaders()
            }
        )
        return true
    }

    private fun getHeaders(): Map<String, String> {
        return mapOf(
            "accept" to "*/*",
            "accept-encoding" to "gzip, deflate, br, zstd",
            "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
            "cache-control" to "no-cache",
            "dnt" to "1",
            "origin" to mainUrl,
            "pragma" to "no-cache",
            "referer" to "$mainUrl/watch/e/",
            "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-fetch-dest" to "empty",
            "sec-fetch-mode" to "cors",
            "sec-fetch-site" to "cross-site",
            "sec-gpc" to "1",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "X-Requested-With" to "XMLHttpRequest",
            "Range" to "bytes=0-",
            "Content-Type" to "application/vnd.apple.mpegurl; charset=utf-8",
            "access-control-allow-origin" to "*",
            "access-control-expose-headers" to "Server, x-goog-meta-frames, Content-Length, Content-Type, Range, X-Requested-With, If-Modified-Since, If-None-Match"
        )
    }

    private suspend fun getProperAnimeLink(uri: String): String? {
        return if (uri.contains("/e/")) {
            app.get(uri).document.selectFirst("div.epcontrol2 a[href*=/a/]")?.attr("href")?.let {
                fixUrl(it)
            }
        } else {
            uri
        }
    }

    private fun String.fixImageUrl(param: Image, episodeNumber: Int? = null): String {
        return when (param) {
            Image.Episode -> {
                val epNum = episodeNumber?.let { String.format("%03d", it) } ?: "001"
                "$posterUrl/images/animes/screens/$this/$epNum.jpg"
            }

            Image.Anime -> {
                "$posterUrl/images/animes/capas/$this.jpg"
            }

            Image.Filme -> {
                "$posterUrl/images/filmes/capas/$this.jpg"
            }
        }
    }

    enum class Image {
        Episode,
        Anime,
        Filme,
    }

    data class Load(
        val slug_serie: String? = null,
        val n_episodio: String? = null,
        val type: String? = null,
    )

    data class DataEpisode(
        @JsonProperty("id_series_episodios") val id_series_episodios: Int? = null,
        @JsonProperty("n_episodio") val n_episodio: String? = null,
        @JsonProperty("titulo_episodio") val titulo_episodio: String? = null,
        @JsonProperty("sinopse_episodio") val sinopse_episodio: String? = null,
        @JsonProperty("generate_id") val generate_id: String? = null,
        @JsonProperty("anime") val anime: HashMap<String, String>? = null,
    )

    data class LoadAnime(
        @JsonProperty("data") val data: ArrayList<DataEpisode>? = arrayListOf()
    )

    data class ApiMeta(
        @JsonProperty("hasNextPage") val hasNextPage: Boolean? = null
    )

    data class ApiItem(
        @JsonProperty("titulo") val titulo: String? = null,
        @JsonProperty("slug_serie") val slug_serie: String? = null,
        @JsonProperty("generate_id") val generate_id: String? = null,
    )

    data class ApiResponse(
        @JsonProperty("meta") val meta: ApiMeta? = null,
        @JsonProperty("data") val data: List<ApiItem>? = null,
    )

    data class EpisodesMeta(
        @JsonProperty("totalOfPages") val totalOfPages: Int? = null
    )

    data class EpisodesResponse(
        @JsonProperty("meta") val meta: EpisodesMeta? = null,
        @JsonProperty("data") val data: List<DataEpisode>? = null,
    )

    data class SearchItem(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("year") val year: String? = null,
        @JsonProperty("censorship") val censorship: Int? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("gen_id") val gen_id: String? = null,
        @JsonProperty("friendly_path") val friendly_path: String? = null,
        @JsonProperty("generic_path") val generic_path: String? = null,
    )

    data class SearchApiResponse(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("meta") val meta: SearchMeta? = null,
        @JsonProperty("message") val message: String? = null,
        @JsonProperty("data") val data: List<SearchItem>? = null,
    )

    data class SearchMeta(
        @JsonProperty("timestamp") val timestamp: Long? = null,
    )

    data class DataMovie(
        @JsonProperty("id_filme") val id_filme: Int? = null,
        @JsonProperty("nome_filme") val nome_filme: String? = null,
        @JsonProperty("slug_filme") val slug_filme: String? = null,
        @JsonProperty("generate_id") val generate_id: String? = null,
    )

    data class FilmesData(
        @JsonProperty("data_movies") val data_movies: List<DataMovie>? = null,
    )

    data class FilmesPageProps(
        @JsonProperty("data") val data: FilmesData? = null,
    )

    data class FilmesResponse(
        @JsonProperty("pageProps") val pageProps: FilmesPageProps? = null,
    )

    data class MovieData(
        @JsonProperty("id_filme") val id_filme: Int? = null,
        @JsonProperty("nome_filme") val nome_filme: String? = null,
        @JsonProperty("nome_original") val nome_original: String? = null,
        @JsonProperty("slug_filme") val slug_filme: String? = null,
        @JsonProperty("ano") val ano: String? = null,
        @JsonProperty("diretor") val diretor: String? = null,
        @JsonProperty("elenco") val elenco: String? = null,
        @JsonProperty("duracao") val duracao: String? = null,
        @JsonProperty("origem") val origem: String? = null,
        @JsonProperty("censura") val censura: String? = null,
        @JsonProperty("sinopse_filme") val sinopse_filme: String? = null,
        @JsonProperty("od") val od: String? = null,
        @JsonProperty("generate_id") val generate_id: String? = null,
    )

    data class MovieApiData(
        @JsonProperty("data_movie") val data_movie: MovieData? = null,
        @JsonProperty("data_user") val data_user: Map<String, Any>? = null,
        @JsonProperty("total_movies") val total_movies: Map<String, Any>? = null,
    )

    data class MovieApiPageProps(
        @JsonProperty("data") val data: MovieApiData? = null,
    )

    data class MovieApiResponse(
        @JsonProperty("pageProps") val pageProps: MovieApiPageProps? = null,
        @JsonProperty("__N_SSG") val __N_SSG: Boolean? = null,
    )
}