package com.EmbedSports

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

class EmbedSports : MainAPI() {
    override var mainUrl = "https://embedcanais.com"
    override var name = "EmbedSports"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)


    private fun createDataUrl(url: String, poster: String) =
        "$url|POSTER:$poster"

    private fun extractPosterFromDataUrl(dataUrl: String) =
        dataUrl.substringAfter("|POSTER:", "")

    private fun extractUrlFromDataUrl(dataUrl: String) =
        dataUrl.substringBefore("|POSTER:")


    private fun parseChannelElements(
        container: org.jsoup.nodes.Element
    ): List<LiveSearchResponse> {
        return container.select(".grid-item").mapNotNull { channel ->
            val title = channel.selectFirst("h3")?.text() ?: return@mapNotNull null
            val url = channel.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = channel.select("img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: channel.select("img").attr("src").takeIf { it.isNotEmpty() }
                ?: ""
            val fullUrl = if (url.startsWith("http")) url else "$mainUrl$url"

            newLiveSearchResponse(title, createDataUrl(fullUrl, poster), TvType.Live) {
                this.posterUrl = poster
            }
        }
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val homePageLists = mutableListOf<HomePageList>()

        doc.select("h4").forEach { header ->
            val title = header.text().trim()
            val container = header.nextElementSibling()
            if (container?.hasClass("grid-container") == true) {
                val channels = parseChannelElements(container)
                if (channels.isNotEmpty()) {
                    homePageLists.add(HomePageList(title, channels))
                }
            }
        }

        return newHomePageResponse(homePageLists)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl).document
        val results = mutableListOf<SearchResponse>()

        doc.select("h4").forEach { header ->
            val container = header.nextElementSibling()
            if (container?.hasClass("grid-container") == true) {
                container.select(".grid-item").forEach { channel ->
                    val title = channel.selectFirst("h3")?.text() ?: return@forEach
                    val url = channel.selectFirst("a")?.attr("href") ?: return@forEach
                    if (!title.contains(query, ignoreCase = true)) return@forEach

                    val poster = channel.select("img").attr("data-src").takeIf { it.isNotEmpty() }
                ?: channel.select("img").attr("src").takeIf { it.isNotEmpty() }
                ?: ""
                    val fullUrl = if (url.startsWith("http")) url else "$mainUrl$url"

                    results.add(
                        newTvSeriesSearchResponse(
                            title,
                            createDataUrl(fullUrl, poster),
                            TvType.Live
                        ) {
                            this.posterUrl = poster
                        }
                    )
                }
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val actualUrl = extractUrlFromDataUrl(url)
        val posterUrl = extractPosterFromDataUrl(url)

        val doc = app.get(actualUrl).document
        val title = doc.selectFirst("h1")?.text() ?: doc.title()

        return newMovieLoadResponse(title, actualUrl, TvType.Live, actualUrl) {
            this.posterUrl = posterUrl
            this.plot = "Canal ao vivo - $title"
            this.dataUrl = actualUrl
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channelUrl = extractUrlFromDataUrl(data).ifEmpty { return false }
        val doc = app.get(channelUrl).document

        val scriptContent = doc.select("script").firstOrNull { script ->
            script.data().contains("source:") && script.data().contains(".m3u8")
        }?.data() ?: return false

        Regex("source:\\s*\"([^\"]+\\.m3u8[^\"]*)\"").find(scriptContent)?.groupValues?.get(1)
            ?.let { url ->
                callback(newExtractorLink("EmbedSports", "EmbedSports Live", url) {
                    this.referer = channelUrl
                    this.headers = defaultHeaders()
                })
                return true
            }

        Regex("embmaxtv\\.online/[^/]+/index\\.m3u8").find(scriptContent)?.value?.let { path ->
            val url = "https://$path"
            callback(newExtractorLink("EmbedSports", "EmbedSports Live", url) {
                this.referer = channelUrl
                this.headers = defaultHeaders()
            })
            return true
        }

        return false
    }

    private fun defaultHeaders() = mapOf(
        "accept" to "*/*",
        "accept-encoding" to "gzip, deflate, br, zstd",
        "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
        "cache-control" to "no-cache",
        "dnt" to "1",
        "origin" to mainUrl,
        "pragma" to "no-cache",
        "referer" to mainUrl,
        "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
        "sec-fetch-dest" to "empty",
        "sec-fetch-mode" to "cors",
        "sec-fetch-site" to "cross-site",
        "sec-gpc" to "1",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
    )
}