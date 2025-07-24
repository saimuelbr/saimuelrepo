package com.Vizer

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.MixDrop
import org.jsoup.nodes.Element
import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class Vizer : MainAPI() {
    override var mainUrl = "https://novizer.com"
    override var name = "Vizer"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "filmes/online" to "Filmes",
        "series/online" to "Séries",
        "animes/online" to "Animes"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}${if (page > 1) "?page=$page" else ""}"
        val doc = app.get(url).document
        val items = doc.select("a.gPoster")
        val list = items.mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, list), hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = attr("href")
        val title = selectFirst("span")?.text() ?: attr("title")
        val img = selectFirst("img")?.attr("src")
        val year = selectFirst(".y")?.text()?.toIntOrNull()
        val type = if (hasClass("serie")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, fixUrl("$mainUrl/$href"), type) {
            this.posterUrl = img?.let { fixUrl("$mainUrl$it") }
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/pesquisar/$query"
        val doc = app.get(url).document
        return doc.select("a.gPoster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isSerie = url.contains("/serie/")
        val rawTitle = doc.selectFirst("h2.one")?.text() ?: doc.selectFirst("h1.type")?.text() ?: ""
        val title = rawTitle.removePrefix("Assistir ").removeSuffix(" Online").trim()
        val stupidPosterImg = doc.selectFirst(".stupidPoster img")?.attr("src")
        val posterAlterado = stupidPosterImg
            ?.replace("/posterPt/", "/background/")
            ?.replace("/342/", "/1280/")
        val poster = if (posterAlterado != null && posterAlterado.startsWith("/")) {
            "$mainUrl$posterAlterado"
        } else {
            posterAlterado
        }
        val plot = doc.selectFirst("span.desc")?.text()
        val year = doc.selectFirst(".year")?.text()?.toIntOrNull()
        val cast = doc.select("#castList .personCard span").map { it.text() }
        val recommendations = doc.select(".bslider-item a.gPoster").mapNotNull { it.toSearchResult() }

        if (isSerie) {
            val seasonElements = doc.select("div.list .item")
            val episodes = mutableListOf<Episode>()
            for ((seasonIndex, seasonElement) in seasonElements.withIndex()) {
                val seasonId = seasonElement.attr("data-season-id").toIntOrNull() ?: continue
                val seasonNumber = seasonIndex + 1
                val epList = getEpisodesForSeason(seasonId).onEach { it.season = seasonNumber }
                episodes.addAll(epList)
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            val downloadBtn = doc.selectFirst("a.btn.click[href*='downloader.php']")
            val downloadUrl = downloadBtn?.attr("href")?.let { if (it.startsWith("/")) "$mainUrl$it" else it }
            val sources = if (downloadUrl != null) listOf(downloadUrl) else listOf(url)
            return newMovieLoadResponse(title, url, TvType.Movie, sources) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun getEpisodesForSeason(seasonId: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val response = app.post(
            "$mainUrl/includes/ajax/publicFunctions.php",
            data = mapOf("getEpisodes" to seasonId.toString()),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<Map<String, Any>>()
        val list = (response["list"] as? Map<*, *>) ?: return episodes
        for ((_, ep) in list) {
            val epMap = ep as? Map<*, *> ?: continue
            val id = epMap["id"]?.toString() ?: continue
            val title = epMap["title"]?.toString() ?: ""
            val epNum = epMap["name"]?.toString()?.toIntOrNull() ?: 0
            episodes.add(newEpisode(id) {
                this.name = title
                this.episode = epNum
            })
        }
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val movieMatch = Regex("movie=(\\d+)").find(data)
        val episodeMatch = Regex("episode=(\\d+)").find(data)
        var isMovie = movieMatch != null
        var id = movieMatch?.groupValues?.get(1) ?: episodeMatch?.groupValues?.get(1)
        var type = if (isMovie) "1" else "2"
        if (id == null) {
            id = Regex("(\\d+)").find(data.substringAfterLast("/"))?.groupValues?.get(1)
            type = "2"
            isMovie = false
        }
        if (id == null) return false
        val response = app.post(
            "$mainUrl/includes/ajax/publicFunctions.php",
            data = mapOf("downloadData" to type, "id" to id),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        var foundAny = false
        try {
            val parsed: List<Map<String, Any?>> = try {
                response.parsed()
            } catch (e: Exception) {
                val obj: Map<String, Map<String, Any?>> = response.parsed()
                obj.values.toList()
            }
            for (row in parsed) {
                val audio = row["audio"]?.toString()
                val redirector = row["redirector"]?.toString()
                val sub = row["sub"]?.toString()
                val originalName = row["originalName"]?.toString()
                if (redirector != null) {
                    val redirectorUrl = if (redirector.startsWith("http")) redirector else "$mainUrl/$redirector"
                    val mixdropUrl = followRedirectToMixDrop(redirectorUrl)
                    if (mixdropUrl != null) {
                        val embedUrl = mixdropUrl.replace(Regex("/f/"), "/e/")
                        var foundLink = false
                        MixDrop().getUrl(embedUrl, mainUrl, subtitleCallback) { originalLink ->
                            foundLink = true
                            foundAny = true
                            val name = when (audio) {
                                "2" -> "MixDrop Dublado"
                                "1" -> "MixDrop Legendado"
                                else -> "MixDrop"
                            }
                            GlobalScope.launch {
                                callback(
                                    newExtractorLink(
                                        originalLink.source,
                                        name,
                                        originalLink.url,
                                        INFER_TYPE
                                    ) {
                                    }
                                )
                            }
                        }
                    }
                }
                if (sub != null && sub.isNotBlank()) {
                    subtitleCallback(SubtitleFile("Legendas", sub))
                }
            }
        } catch (e: Exception) {
        }
        return foundAny
    }

    private suspend fun followRedirectToMixDrop(url: String): String? {
        var currentUrl = url
        repeat(5) {
            val response = app.get(currentUrl, allowRedirects = false)
            val location = response.headers["location"]
            if (location != null && location.contains("mixdrop")) {
                return location
            }
            if (location != null) {
                currentUrl = location
            } else {
                val doc = response.document
                val iframe = doc.selectFirst("iframe[src*='mixdrop']")
                if (iframe != null) {
                    var link = iframe.attr("src")
                    link = link.replace("mixdrop.ag", "mixdrop.my")
                    return link
                }
                val meta = doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                if (meta != null && meta.contains("url=")) {
                    val redirectUrl = meta.substringAfter("url=")
                    if (redirectUrl.contains("mixdrop")) {
                        val link = redirectUrl.replace("mixdrop.ag", "mixdrop.my")
                        return link
                    }
                    currentUrl = redirectUrl
                    return@repeat
                }
                val aMix = doc.selectFirst("a[href*='mixdrop']")?.attr("href")
                if (aMix != null) {
                    val link = aMix.replace("mixdrop.ag", "mixdrop.my")
                    return link
                }
                val scriptMix = doc.select("script").mapNotNull { script ->
                    Regex("window\\.location\\.href\\s*=\\s*\"([^\"]+)\"").find(script.data())?.groupValues?.get(1)
                }.firstOrNull { it.contains("mixdrop") }
                if (scriptMix != null) {
                    val link = scriptMix.replace("mixdrop.ag", "mixdrop.my")
                    return link
                }
                return null
            }
        }
        return null
    }
} 