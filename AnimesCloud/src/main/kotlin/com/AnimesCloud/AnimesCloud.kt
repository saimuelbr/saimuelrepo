package com.AnimesCloud

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnimesCloud : MainAPI() {
    override var mainUrl = "https://animesonline.cloud"
    override var name = "AnimesCloud"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    private val cloudflareInterceptor = CloudflareKiller()
    
    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val locker = Mutex()
        private var isInitialized = false
        private val posterLock = Mutex()
        private var requestCounter = 0
    }

    private var persistedCookies: String? = null

    private val defaultHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/",
            "Cookie" to (persistedCookies ?: "")
        )

    private suspend fun request(url: String): Document {
        if (!isInitialized) {
            locker.withLock {
                if (!isInitialized) {
                    try {
                        val resMain = app.get(mainUrl, headers = mapOf("User-Agent" to USER_AGENT), interceptor = cloudflareInterceptor, timeout = 60)
                        if (resMain.code == 200) {
                            val cookieList = mutableListOf<String>()
                            resMain.okhttpResponse.headers("Set-Cookie").forEach { cookieList.add(it.split(";")[0]) }
                            resMain.okhttpResponse.request.header("Cookie")?.let { cookieList.add(it) }
                            persistedCookies = cookieList.distinct().joinToString("; ")
                            isInitialized = true
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return app.get(url, headers = defaultHeaders, interceptor = cloudflareInterceptor).document
    }

    private suspend fun getPoster(title: String?): String? {
        if (title.isNullOrBlank()) return null
        val cleanTitle = title.replace(Regex("(?i)^(Home|Animes|Filmes|Online)\\s+"), "")
            .replace(Regex("(?i)(Dublado|Legendado|Online|HD|TV|Todos os Episódios|Filme|\\d+ª Temporada|\\d+ª|Completo|\\d+$)"), "")
            .trim()

        return posterLock.withLock {
            kotlinx.coroutines.delay(111)
            val turn = requestCounter % 9
            val useKitsu = (turn == 1 || turn == 2 || turn == 4 || turn == 5 || turn == 7 || turn == 8)
            requestCounter++

            if (useKitsu) {
                try {
                    val url = "https://kitsu.io/api/edge/anime?filter[text]=${cleanTitle.replace(" ", "%20")}"
                    val response = app.get(url, timeout = 10)
                    if (response.code == 200) {
                        Regex("""posterImage[^}]*original":"(https:[^"]+)""").find(response.text)?.groupValues?.get(1)?.replace("\\/", "/")
                    } else null
                } catch (_: Exception) { null }
            } else {
                try {
                    val url = "https://api.jikan.moe/v4/anime?q=${cleanTitle.replace(" ", "%20")}&limit=1"
                    val response = app.get(url, timeout = 10)
                    if (response.code == 200) {
                        Regex("""large_image_url":"(https:[^"]+)""").find(response.text)?.groupValues?.get(1)?.replace("\\/", "/")
                    } else null
                } catch (_: Exception) { null }
            }
        }
    }

    override val mainPage = mainPageOf("tipo/dublado" to "Dublados", "tipo/legendado" to "Legendados")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = request("$mainUrl/${request.data}")
        val items = document.select("div.items article, div.content div.items article")
        val home = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, home, false), false)
    }

    private suspend fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data h3 a")?.attr("href") ?: return null)
        val scoreValue = this.selectFirst("div.rating")?.text()?.toDoubleOrNull()
        val poster = getPoster(title)

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = poster
            this.quality = SearchQuality.HD
            this.score = Score.from10(scoreValue)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/?s=${query.replace(" ", "+")}")
        return document.select("div.search-page div.result-item article").mapNotNull { item ->
            val title = item.selectFirst("div.details div.title a")?.text()?.trim() ?: return@mapNotNull null
            val href = fixUrl(item.selectFirst("div.details div.title a")?.attr("href") ?: return@mapNotNull null)
            val yearVal = item.selectFirst("div.meta span.year")?.text()?.trim()?.toIntOrNull()
            val poster = getPoster(title)

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
                this.year = yearVal
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url)
        val title = document.selectFirst("h1")?.text()?.trim() ?: ""
        val scoreValue = document.select("b#repimdb strong").text().toDoubleOrNull()
        val durationText = document.select("div.custom_fields").find { it.text().contains("Duração") }?.selectFirst("span.valor")?.text()
        
        val actors = document.select("div.person").mapNotNull {
            val name = it.selectFirst("div.data div.name a")?.text() ?: return@mapNotNull null
            val role = it.selectFirst("div.data div.caracter")?.text() ?: ""
            val img = it.selectFirst("div.img img")?.attr("src")
            Pair(Actor(name, img), role)
        }

        val isMovie = url.contains("/filme/") || document.select("div#episodes").isEmpty()
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        val plotText = document.selectFirst("div.wp-content p:nth-child(2)")?.text()?.trim() ?: document.selectFirst("div.wp-content")?.text()?.trim()
        val finalPoster = document.selectFirst("div.g-item a")?.attr("href")?.trim()

        return if (isMovie) {
            newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = finalPoster
                this.plot = plotText
                this.score = Score.from10(scoreValue)
                addDuration(durationText)
                addActors(actors)
            }
        } else {
            val episodes = loadEpisodesFromPage(document, url)
            newAnimeLoadResponse(title, url, type) {
                this.posterUrl = finalPoster
                this.plot = plotText
                this.score = Score.from10(scoreValue)
                addDuration(durationText)
                addActors(actors)
                addEpisodes(DubStatus.Subbed, episodes[DubStatus.Subbed] ?: emptyList())
                addEpisodes(DubStatus.Dubbed, episodes[DubStatus.Dubbed] ?: emptyList())
            }
        }
    }

    private fun loadEpisodesFromPage(document: Document, baseUrl: String): Map<DubStatus, List<Episode>> {
        val elements = document.select("div#episodes ul.episodios li")
        val episodeList = elements.map { episodeElement ->
            val episodeUrl = fixUrl(episodeElement.select("div.episodiotitle a").attr("href"))
            val numText = episodeElement.select("div.numerando").text()
            val match = Regex("""(\d+)\s*-\s*(\d+)""").find(numText)
            
            newEpisode(episodeUrl) {
                this.name = episodeElement.select("div.episodiotitle a").text().trim()
                this.episode = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                this.season = match?.groupValues?.get(1)?.toIntOrNull() ?: 1 
            }
        }
        return mapOf((if (baseUrl.contains("dublado")) DubStatus.Dubbed else DubStatus.Subbed) to episodeList)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return AnimesCloudExtractor.extractVideoLinks(data, mainUrl, name, callback)
    }
}