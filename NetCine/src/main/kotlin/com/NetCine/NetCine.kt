package com.NetCine

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NetCine : MainAPI() {

    override var mainUrl = NETCINE_URL
    override var name = "NetCine"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.TvSeries)

    companion object {
        const val NETCINE_URL = "https://nnn1.lat"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        
        val iframeRegex = Regex("""<div\s+id="(play-\d+)"[^>]*>.*?<iframe\s+src="([^"]+)""", RegexOption.DOT_MATCHES_ALL)
        val labelRegex = Regex("""<a\s+href="#(play-\d+)">([^<]+)</a>""")
        val videoSourceRegex = Regex("""<source\s+[^>]*src=["']([^"']+)["']""")
        val nextRegex = Regex("""href\s*=\s*["']([^"']*(?:hls\.php|hlsarchive\.php\?hls|gc\d+\.php|playerarchive\.php)[^"']*)["']""")

        val defaultHeaders = mapOf(
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "cookie" to "XCRF=XCRF; PHPSESSID=v8fk5egon2jcqo69hs7d9cail1",
            "user-agent" to USER_AGENT
        )
    }

    override val mainPage = mainPageOf(
        "category/ultimos-filmes" to "Últimas Atualizações Filmes",
        "category/acao" to "Ação",
        "category/animacao" to "Animação",
        "category/aventura" to "Aventura",
        "category/comedia" to "Comédia",
        "category/crime" to "Crime",
        "tvshows" to "Últimas Atualizações Séries",
        "tvshows/category/acao" to "Séries de Ação",
        "tvshows/category/animacao" to "Séries de Animação",
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }

        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").document
        val items = document.select("#box_movies > div.movie").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, items), true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document
            .select("#box_movies > div.movie")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isTv = url.contains("tvshows") || url.contains("/episode/")
        
        val title = doc.selectFirst("div.dataplus h1, div.dataplus span.original")?.text() ?: ""
        val poster = fixUrl(doc.select("div.headingder > div.cover").attr("data-bg"))
        val plot = doc.selectFirst("#dato-2 p")?.text()?.trim()
        val year = doc.select("#dato-1 > div:nth-child(5)").text().trim().toIntOrNull()
        val score = doc.selectFirst("div.rank")?.text()?.toDoubleOrNull()
        
        val recommendations = doc.select("div.links a").mapNotNull {
            newMovieSearchResponse(it.selectFirst("h4")?.text() ?: return@mapNotNull null, it.attr("href"), TvType.Movie) {
                this.posterUrl = it.selectFirst("img")?.attr("src")
            }
        }

        return if (isTv) {
            val episodes = doc.select("div.post #cssmenu > ul li > ul > li").mapNotNull {
                val epHref = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val dateText = it.select("a > span.datex").text()
                newEpisode(epHref) {
                    name = it.select("a > span.datix").text().trim()
                    season = dateText.substringBefore("-").filter { it.isDigit() }.toIntOrNull()
                    episode = dateText.substringAfter("-").filter { it.isDigit() }.toIntOrNull()
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                fillMeta(poster, plot, year, score, recommendations)
                addActors(doc.select("#dato-1 > div:nth-child(4) a").map { it.text() })
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                fillMeta(poster, plot, year, score, recommendations)
                addActors(doc.select("#dato-1 > div:nth-child(4) a").map { it.text() })
            }
        }
    }

    private fun LoadResponse.fillMeta(p: String, pl: String?, y: Int?, s: Double?, rec: List<SearchResponse>) {
        this.posterUrl = p
        this.plot = pl
        this.year = y
        this.score = s?.let { Score.from10(it) }
        this.recommendations = rec
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val sessionHeaders = defaultHeaders.toMutableMap().apply { put("referer", "$mainUrl/") }
        
        val html = app.get(data, headers = sessionHeaders).text
        val iframes = iframeRegex.findAll(html).toList()
        if (iframes.isEmpty()) return false

        val labels = labelRegex.findAll(html).associate { 
            it.groupValues[1] to it.groupValues[2].trim() 
        }

        iframes.sortedByDescending { 
            labels[it.groupValues[1]]?.contains("Dub", true) == true 
        }.forEach { match ->
            val playId = match.groupValues[1]
            val label = labels[playId] ?: "Player"
            val iframeUrl = fixUrl(match.groupValues[2])

            val res2 = app.get(iframeUrl, headers = sessionHeaders.toMutableMap().apply { put("referer", data) })
            var videoUrl = videoSourceRegex.find(res2.text)?.groupValues?.get(1)
            var ref = data

            if (videoUrl.isNullOrEmpty()) {
                nextRegex.find(res2.text)?.groupValues?.get(1)?.let { path ->
                    val absNextUrl = fixUrl(path)
                    val res3 = app.get(absNextUrl, headers = sessionHeaders.toMutableMap().apply {
                        put("referer", iframeUrl)
                        put("cookie", "XCRF=XCRF; PHPSESSID=3o6atiuojr31rthqvefimlhtl8")
                    })
                    
                    videoUrl = videoSourceRegex.find(res3.text)?.groupValues?.get(1)
                    ref = iframeUrl
                }
            }

            videoUrl?.let { url ->
                val isM3u = url.contains(".m3u8") || url.contains(".php")
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} $label",
                        url = url,
                        type = if (isM3u) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/" 
                        this.headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Referer" to ref
                        )
                    }
                )
            }
        }
        return true
    }
}