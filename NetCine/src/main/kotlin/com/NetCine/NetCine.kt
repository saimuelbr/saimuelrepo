package com.NetCine

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
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

class NetCine : MainAPI() {
    override var mainUrl = "https://neetx.lol"
    override var name = "NetCine"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "category/ultimos-filmes" to "Últimas Atualizações Filmes",
        "category/acao" to "Ação Filmes",
        "category/animacao" to "Animação Filmes",
        "category/aventura" to "Aventura Filmes",
        "category/comedia" to "Comédia Filmes",
        "category/crime" to "Crime Filmes",
        "tvshows" to "Últimas Atualizações Séries",
        "tvshows/category/acao" to "Ação Séries",
        "tvshows/category/animacao" to "Animação Séries",
        "tvshows/category/aventura" to "Aventura Séries",
        "tvshows/category/comedia" to "Comédia Séries",
        "tvshows/category/crime" to "Crime Séries",
    )


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document

        val home = document.select("#box_movies > div.movie").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl =
            this.select("img").attr("data-src").ifEmpty { this.select("img").attr("src") }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val document = app.get("${mainUrl}/?s=$query").document
            val results = document.select("#box_movies > div.movie").mapNotNull { it.toSearchResult() }
            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }
            if (results.isEmpty()) break
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.dataplus h1")?.text() ?: document.select("div.dataplus span.original").text()
        val poster = fixUrl(document.select("div.headingder > div.cover").attr("data-bg"))
        val description = document.selectFirst("#dato-2 p")?.text()?.trim()
        val type = if (url.contains("tvshows")) TvType.TvSeries else TvType.Movie
        val imdbid = document.selectFirst("div.imdbdatos a")?.attr("href")?.substringAfterLast("/")
        val actors=document.select("#dato-1 > div:nth-child(4)").map { it.select("a").text() }
        val duration = document.select("#dato-1 p span").find { span ->
            span.select("b.icon-query-builder").isNotEmpty()
        }?.text()?.trim()
        val recommendations=document.select("div.links a").amap {
            val recName = it.select("div.data-r > h4").text()
            val recHref = it.attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName,recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }
        val year = document.select("#dato-1 > div:nth-child(5)").text().toIntOrNull()
            if (type == TvType.TvSeries) {
                val episodes = mutableListOf<Episode>()
                document.select("div.post #cssmenu > ul li > ul > li").map {
                    val seasonno = it.select("a > span.datex").text().substringBefore("-").trim()
                        .toIntOrNull()
                    val episodeno= it.select("a > span.datex").text().substringAfterLast("-").trim()
                        .toIntOrNull()
                    val epname=it.select("a > span.datix").text()
                    val ephref = it.selectFirst("a")?.attr("href")
                    episodes += newEpisode(ephref)
                    {
                        this.name = epname
                        this.season = seasonno
                        this.episode = episodeno
                    }
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year=year
                    this.recommendations=recommendations
                    addActors(actors)
                    addImdbId(imdbid)
                    if (!duration.isNullOrBlank()) {
                        addDuration(duration)
                    }
                }
            }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year=year
            this.recommendations=recommendations
            addActors(actors)
            addImdbId(imdbid)
            if (!duration.isNullOrBlank()) {
                addDuration(duration)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val playerContainer = doc.selectFirst("#player-container")
        if (playerContainer == null) {
            Log.d("Error:", "Player container not found")
            return false
        }

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "Upgrade-Insecure-Requests" to "1"
        )

        val cookies = mapOf(
            "XCRF" to "XCRF",
            "PHPSESSID" to "72um6ie78l4udrsku00gba1aiv"
        )

        val playerMenu = playerContainer.select("ul.player-menu li a")
        val playerContents = playerContainer.select("div.player-content")

        val audioTypes = mutableListOf<Pair<String, String>>()
        
        for (menuItem in playerMenu) {
            val audioType = menuItem.text().trim()
            val playerId = menuItem.attr("href").substringAfter("#")
            audioTypes.add(audioType to playerId)
        }

        val dubladoPlayer = audioTypes.find { it.first.contains("Dublado", ignoreCase = true) }
        val legendadoPlayer = audioTypes.find { it.first.contains("Legendado", ignoreCase = true) }

        val playersToProcess = mutableListOf<Pair<String, String>>()
        
        if (dubladoPlayer != null) {
            playersToProcess.add(dubladoPlayer)
        }
        if (legendadoPlayer != null) {
            playersToProcess.add(legendadoPlayer)
        }

        if (playersToProcess.isEmpty()) {
            playersToProcess.addAll(audioTypes)
        }

        var hasValidLinks = false

        for ((audioType, playerId) in playersToProcess) {
            val playerContent = playerContents.find { it.attr("id") == playerId }
            if (playerContent == null) continue

            val iframe = playerContent.selectFirst("iframe")
            if (iframe == null) continue

            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isBlank()) continue

            val fullIframeUrl = if (iframeSrc.startsWith("http")) iframeSrc else "$mainUrl/$iframeSrc"
            
            try {
                val iframeDoc = app.get(fullIframeUrl, headers = headers, cookies = cookies).document
                val videoWrapper = iframeDoc.selectFirst("div.plyr__video-wrapper")
                if (videoWrapper == null) continue

                val videoSource = videoWrapper.selectFirst("video source")
                if (videoSource == null) continue

                val videoUrl = videoSource.attr("src")
                if (videoUrl.isBlank()) continue

                val sourceName = "$name $audioType"
                callback.invoke(
                    newExtractorLink(
                        sourceName,
                        sourceName,
                        videoUrl,
                        com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                    }
                )
                hasValidLinks = true
                
            } catch (e: Exception) {
                Log.e("Error:", "Error processing player $playerId ($audioType): $e")
            }
        }

        return hasValidLinks
    }
}
