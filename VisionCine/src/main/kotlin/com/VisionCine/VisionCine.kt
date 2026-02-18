package com.VisionCine

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

object VisionCineSession {
    private const val INTERNAL_DRM_ID = "pygrp_KJp_cyHo0.lbp-kBz.mo52lYEgGDK1tDG9tb_9GXI_"

    val okClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun updateLayoutConstraints(p0: String): String {
        val os = 5
        val std = intArrayOf(
            60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 
            92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 
            43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 38, 42
        ).map { (it + os).toChar() }.joinToString("")

        val op = 3
        val tp = intArrayOf(
            119, 62, 117, 63, 118, 64, 116, 65, 115, 66, 114, 67, 113, 68, 112, 69, 111, 70, 110, 71, 109, 72, 108, 73, 107, 75, 106, 74, 105, 104, 103, 102, 101, 100, 99, 98, 97, 96, 95, 94, 
            45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 40, 42, 92, 87, 85, 86, 84, 43, 39, 30, 33, 32, 34, 35
        ).map { (it + op).toChar() }.joinToString("")
        
        val sb = StringBuilder()
        for (c in p0) {
            val i = tp.indexOf(c)
            if (i != -1 && i < std.length) {
                sb.append(std[i])
            } else {
                sb.append(when(c) { '!' -> '+'; '$' -> '/'; else -> c })
            }
        }
        
        var buffer = sb.toString()
        while (buffer.length % 4 != 0) buffer += "="
        

        return try {
            val offset = 10
            val encodedClassName = intArrayOf(87, 100, 90, 104, 101, 95, 90, 36, 107, 106, 95, 98, 36, 56, 87, 105, 91, 44, 42)
            val decodedClassName = encodedClassName.map { (it + offset).toChar() }.joinToString("")
            val m = intArrayOf(90, 91, 89, 101, 90, 91).map { (it + offset).toChar() }.joinToString("")
            
            val clazz = Class.forName(decodedClassName)
            val method = clazz.getMethod(m, String::class.java, Int::class.javaPrimitiveType)
            val raw = method.invoke(null, buffer, 0) as ByteArray
            val res = String(raw, Charsets.UTF_8).trim()
            res
        } catch (e: Exception) { "" }
    }

    val appConfigToken by lazy { updateLayoutConstraints(INTERNAL_DRM_ID) }

    fun buildHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        return mutableMapOf(
            "User-Agent" to (com.lagradost.cloudstream3.network.WebViewResolver.webViewUserAgent ?: "Mozilla/5.0"),
            "Cookie" to appConfigToken
        ).apply { putAll(extra) }
    }

    suspend fun get(url: String, referer: String? = null, headers: Map<String, String> = emptyMap()): com.lagradost.nicehttp.NiceResponse {
        val h = buildHeaders(headers).toMutableMap()
        referer?.let { h["Referer"] = it }
        return com.lagradost.nicehttp.Requests(okClient).get(url, headers = h)
    }
}

class VisionCine : MainAPI() {
    override var mainUrl = "https://cnvsweb.stream"
    override var name = "VisionCine"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    
    override val mainPage = mainPageOf(
        "207" to "4k",
        "85" to "Ação",
        "94" to "Animes",
        "191" to "Apple TV",
        "101" to "Brasileiro",
        "92" to "Comédia",
        "97" to "Crime",
        "84" to "DC", 
        "197" to "Doramas",
        "87" to "Drama",
        "161" to "Guerra",
        "83" to "Marvel",
        "198" to "Novelas",
        "80" to "TeleCine",
        "73" to "Netflix",
        "75" to "Amazon Prime",
        "81" to "HBO",
        "76" to "GloboPlay"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/ajax/genre.php?genre=${request.data}&page=$page"
        val response = VisionCineSession.get(url).text
        val json = parseJson<List<GenreResponse>>(response)
        
        val list = json.map { item ->
            val isSeries = item.time?.contains("Temporadas", ignoreCase = true) == true
            val type = if (isSeries) TvType.TvSeries else TvType.Movie
            val loadUrl = "$mainUrl/watch/${item.slug}"
            val highResPoster = item.image?.replace("/w300/", "/original/")

            newMovieSearchResponse(item.title ?: "", loadUrl, type) {
                this.posterUrl = highResPoster
                this.year = item.release?.toIntOrNull()
                this.score = com.lagradost.cloudstream3.Score.from10(item.imdb_rating?.toDoubleOrNull())
            }
        }
        
        return newHomePageResponse(
            HomePageList(request.name, list), 
            hasNext = list.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a.btn.free") ?: selectFirst("a.btn.free.fw-bold")
        val href = a?.attr("href") ?: return null
        val title = selectFirst("h6")?.text() ?: return null
        
        val imgStyle = selectFirst(".content")?.attr("style")
        val img = imgStyle?.let { Regex("url\\((.*?)\\)").find(it)?.groupValues?.getOrNull(1) }
            ?.replace("/w300/", "/original/")
            
        val scoreValue = select("span").find { it.text().contains("IMDb") }
            ?.text()?.replace("IMDb", "")?.trim()?.toDoubleOrNull()
            
        val tags = selectFirst(".tags")?.text() ?: ""
        val year = Regex("\\d{4}").find(tags)?.value?.toIntOrNull()
        val type = if (tags.contains("Temporada", ignoreCase = true) || tags.contains("Temporadas", ignoreCase = true)) 
            TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = img
            this.year = year
            this.score = com.lagradost.cloudstream3.Score.from10(scoreValue)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search.php?q=$query"
        val doc = Jsoup.parse(VisionCineSession.get(url).text)
        return doc.select("section.listContent .item.poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = VisionCineSession.get(url)
        val doc = Jsoup.parse(response.text)
        
        val title = doc.selectFirst("h1.fw-bolder")?.text() ?: ""
        val plot = doc.selectFirst("p.small.linefive")?.text()
        val year = doc.select("p.log span").mapNotNull { it.text().toIntOrNull() }.firstOrNull()
        
        val posterStyle = doc.selectFirst(".backImage")?.attr("style")
        val poster = posterStyle?.let { Regex("url\\('(.+?)'\\)").find(it)?.groupValues?.getOrNull(1) }
            ?.replace("/w300/", "/original/")
        
        val genres = doc.select(".producerInfo p.lineone").filter { 
            it.selectFirst("span")?.text()?.contains("Gênero", ignoreCase = true) == true 
        }.flatMap { p -> 
            p.select("span span").map { it.text().trim() } 
        }.toMutableList()

        val quality = doc.select("span").find { 
            val t = it.text()
            t == "HD" || t == "SD" || t == "FHD"
        }?.text()?.replace("HD", "FHD")

        quality?.let { genres.add(it) }

        val scoreValue = doc.select("span").find { it.text().contains("IMDb") }
            ?.text()?.replace("IMDb", "")?.trim()?.toDoubleOrNull()

        val duration = doc.select("span").find { it.text().contains("Min", ignoreCase = true) }
            ?.text()?.replace("Min", "", ignoreCase = true)?.trim()?.toIntOrNull()
        
        val isSerie = url.contains("/series") || doc.selectFirst("#seasons-view") != null
        
        val recommendations = doc.select("div.swiper-slide.item").mapNotNull {
            val recTitle = it.selectFirst("h6")?.text() ?: return@mapNotNull null
            val recHref = it.selectFirst("div.buttons a")?.attr("href") ?: return@mapNotNull null
            val recStyle = it.selectFirst("div.content")?.attr("style") ?: ""
            val recPoster = Regex("""url\(['"]?([^'"]+)['"]?\)""").find(recStyle)?.groupValues?.get(1)
                ?.replace("/w300/", "/original/")

            val isRecSeries = it.select("div.tags span").any { span -> 
                span.text().contains("Temporada", ignoreCase = true) 
            }
            
            newMovieSearchResponse(recTitle, recHref, if (isRecSeries) TvType.TvSeries else TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        if (isSerie) {
            val seasons = doc.select("#seasons-view option").mapNotNull { it.attr("value").toIntOrNull() }
            val episodes = mutableListOf<Episode>()
            for ((seasonIndex, seasonId) in seasons.withIndex()) {
                val epList = getEpisodesForSeason(seasonId, seasonIndex + 1)
                episodes.addAll(epList)
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.score = com.lagradost.cloudstream3.Score.from10(scoreValue)
                this.recommendations = recommendations
            }
        } else {
            val watchBtn = doc.selectFirst("div.buttons a.btn.free[href*='/m/']")
            val watchUrl = watchBtn?.attr("href")
            
            val finalDataUrl = if (!watchUrl.isNullOrEmpty()) {
                if (watchUrl.startsWith("http")) watchUrl else "http://www.playcnvs.stream$watchUrl"
            } else { url }

            return newMovieLoadResponse(title, url, TvType.Movie, finalDataUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.score = com.lagradost.cloudstream3.Score.from10(scoreValue)
                this.duration = duration
                this.recommendations = recommendations
            }
        }
    }

    private suspend fun getEpisodesForSeason(seasonId: Int, seasonNumber: Int): List<Episode> {
        val url = "$mainUrl/ajax/episodes.php?season=$seasonId&page=1"
        val doc = Jsoup.parse(VisionCineSession.get(url, url).text)
        val eps = doc.select("div.ep")
        
        return eps.mapNotNull { ep ->
            val epNum = ep.selectFirst("p[number]")?.text()?.toIntOrNull() ?: 0
            val name = ep.selectFirst("h5.fw-bold")?.text() ?: "Episódio $epNum"
            val playBtn = ep.selectFirst("a.btn.free.fw-bold, a.btn.free")
            val episodeUrl = playBtn?.attr("href") ?: return@mapNotNull null
            
            newEpisode(episodeUrl) {
                this.name = name
                this.episode = epNum
                this.season = seasonNumber
                this.data = episodeUrl
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val episodeUrl = if (data.startsWith("[")) {
                data.removePrefix("[").removeSuffix("]").removeSurrounding("\"").split("|").last()
            } else {
                data
            }

            val baseHeaders = Headers.Builder()
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                .add("Cookie", VisionCineSession.appConfigToken)
                .add("Referer", "https://www.visioncine-1.com.br/")
                .build()

            val request = okhttp3.Request.Builder()
                .url(episodeUrl)
                .headers(baseHeaders)
                .build()

            val response = VisionCineSession.okClient.newCall(request).execute()
            val htmlBruto = response.body?.string() ?: ""
            val doc = Jsoup.parse(htmlBruto)
            
            val sources = mutableListOf<String>()
            
            val dropdownSources = doc.select(".sources-dropdown .dropdown-menu a.source-btn")
            
            dropdownSources.forEach { source ->
                val href = source.attr("href")
                val sourceText = source.ownText().trim() 
                val badgeText = source.select("label.badge").text().trim()
                val fullText = if (badgeText.isNotEmpty()) "$sourceText ($badgeText)" else sourceText

                if (href.isNotEmpty() && !href.startsWith("#")) {
                    val absoluteUrl = if (href.startsWith("http")) href else "http://www.playcnvs.stream$href"
                    sources.add("$fullText|$absoluteUrl")
                }
            }

            if (sources.isEmpty()) {
                doc.select("a.btn.free").forEach { btn ->
                    val href = btn.attr("href")
                    if ((href.contains("/s/") || href.contains("/m/")) && !href.contains("history.go")) {
                        val absoluteUrl = if (href.startsWith("http")) href else "http://www.playcnvs.stream$href"
                        sources.add("${btn.text()}|$absoluteUrl")
                    }
                }
            }
            
            var foundAny = false

            val sortedSources = sources.sortedByDescending { it.contains("4K", ignoreCase = true) }
            
            for (sourceData in sortedSources) {
                val parts = sourceData.split("|")
                if (parts.size < 2) continue
                val name = parts[0]
                val playerUrl = parts[1]
                
                try {
                    val pReq = okhttp3.Request.Builder()
                        .url(playerUrl)
                        .headers(baseHeaders)
                        .addHeader("Referer", episodeUrl)
                        .build()

                    val pRes = VisionCineSession.okClient.newCall(pReq).execute()
                    val pHtml = pRes.body?.string() ?: ""
                    
                    val scripts = Jsoup.parse(pHtml).select("script")
                    val scriptContent = scripts.map { it.data() }.joinToString("\n")

                    val patterns = listOf(
                        Regex("initializePlayerWithSubtitle\\(['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"],\\s*['\"]([^'\"]*\\.srt[^'\"]*)['\"]"),
                        Regex("initializePlayer\\(['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                        Regex("file:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                        Regex("src:\\s*['\"]([^'\"]*\\.(?:mp4|m3u8)[^'\"]*)['\"]"),
                        Regex("[\"']?file[\"']?\\s*:\\s*[\"']([^\"']+)[\"']"),
                        Regex("[\"']?url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']")
                    )
                    
                    var foundInThisSource = false
                    for (pattern in patterns) {
                        val match = pattern.find(scriptContent)
                        if (match != null) {
                            val videoUrl = match.groupValues[1].replace("\\/", "/")
                            val subUrl = match.groupValues.getOrNull(2)?.replace("\\/", "/")
                            
                            if (subUrl != null) subtitleCallback(SubtitleFile("pt", subUrl))
                            
                            callback(newExtractorLink(
                                "VisionCine - $name", 
                                "VisionCine - $name", 
                                videoUrl
                            ))
                            
                            foundAny = true
                            foundInThisSource = true
                            break
                        }
                    }
                } catch (e: Exception) { 
                }
            }
            return foundAny
        } catch (e: Exception) {
            return false
        }
    }

    data class GenreResponse(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("imdb_rating") val imdb_rating: String? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("release") val release: String? = null,
        @JsonProperty("time") val time: String? = null
    )
}