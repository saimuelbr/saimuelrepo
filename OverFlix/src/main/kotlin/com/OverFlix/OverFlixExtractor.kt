package com.OverFlix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class OverFlixExtractor {

    companion object {
        private const val OPTIONS_API = "https://fshd.link/api/options"
        private const val PLAYER_API = "https://fshd.link/api/players"

        private val AJAX_HEADERS = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json"
        )
    }

    suspend fun extractLinks(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframeUrl = extractIframe(url) ?: return false

        val embedRes = app.get(iframeUrl, referer = url)
        val embedDoc = embedRes.document
        val embedHtml = embedRes.text

        val isTv = isTvContent(url, iframeUrl)
        val contentType = if (isTv) "2" else "1"
        val contentInfo = extractContentInfo(isTv, iframeUrl, embedDoc, embedHtml)

        val serverIds = if (isTv) {
            fetchSeriesServerIds(contentInfo, iframeUrl)
        } else {
            fetchMovieServerIds(embedDoc)
        }

        serverIds.distinct().forEach { videoId ->
            runCatching {
                val playerUrl = fetchPlayerUrl(
                    contentInfo,
                    contentType,
                    videoId,
                    iframeUrl
                ) ?: return@forEach

                val finalUrl = resolveRedirect(playerUrl, iframeUrl) ?: return@forEach

                if (isInternalPlayer(finalUrl)) {
                    extractInternalPlayer(finalUrl, playerUrl, callback)
                } else {
                    loadExtractor(finalUrl, playerUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    private suspend fun extractIframe(url: String): String? {
        val doc = app.get(url).document
        val src = doc.selectFirst(
            "iframe[src*=/filme/], iframe[src*=/v/], iframe[src*=/serie/]"
        )?.attr("src")
            ?: doc.selectFirst("div.aspect-video iframe")?.attr("src")

        return src?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun isTvContent(pageUrl: String, iframeUrl: String): Boolean {
        return pageUrl.contains("/episodio/") || iframeUrl.contains("/serie/")
    }

    private fun extractContentInfo(
        isTv: Boolean,
        iframeUrl: String,
        embedDoc: org.jsoup.nodes.Document,
        embedHtml: String
    ): String {
        return if (isTv) {
            embedDoc.selectFirst(".episodeOption.active")
                ?.attr("data-contentid")
                ?: Regex("var CONTENT_INFO = '(\\d+)';")
                    .find(embedHtml)
                    ?.groupValues
                    ?.get(1)
        } else {
            Regex("var CONTENT_INFO = '(\\d+)';")
                .find(embedHtml)
                ?.groupValues
                ?.get(1)
        } ?: iframeUrl.split("/").lastOrNull()
            ?.split("?")
            ?.firstOrNull()
            .orEmpty()
    }

    private suspend fun fetchSeriesServerIds(
        contentInfo: String,
        referer: String
    ): List<String> {
        val response = app.post(
            OPTIONS_API,
            headers = AJAX_HEADERS,
            json = mapOf(
                "content_id" to (contentInfo.toIntOrNull() ?: 0),
                "content_type" to "2"
            ),
            referer = referer
        ).text

        return Regex("""["']ID["']\s*:\s*(\d+)""")
            .findAll(response)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun fetchMovieServerIds(
        doc: org.jsoup.nodes.Document
    ): List<String> {
        return doc.select("div.server-selector, .audio-selector")
            .mapNotNull { it.attr("data-id").takeIf(String::isNotEmpty) }
    }

    private suspend fun fetchPlayerUrl(
        contentInfo: String,
        contentType: String,
        videoId: String,
        referer: String
    ): String? {
        val response = app.post(
            PLAYER_API,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            json = mapOf(
                "content_info" to (contentInfo.toIntOrNull() ?: 0),
                "content_type" to contentType,
                "video_id" to (videoId.toIntOrNull() ?: 0)
            ),
            referer = referer
        ).text

        return Regex("""["']video_url["']\s*:\s*["'](.*?)["']""")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.replace("\\/", "/")
    }

    private suspend fun resolveRedirect(
        playerUrl: String,
        referer: String
    ): String? {
        val page = app.get(playerUrl, referer = referer).text
        return Regex("""window\.location\.href\s*=\s*"([^"]+)""")
            .find(page)
            ?.groupValues
            ?.get(1)
    }

    private fun isInternalPlayer(url: String): Boolean {
        return url.contains("112234152.xyz") || url.contains("/player/")
    }

    private suspend fun extractInternalPlayer(
        finalUrl: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(finalUrl, referer = referer).text

        val baseUrl = Regex("""var player_base_url\s*=\s*"([^"]+)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        val packedJs = Regex(
            """eval\s*\((function\(p,a,c,k,e,d\).+?)\)\s*;?\s*</script>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1).orEmpty()

        if (packedJs.isEmpty()) return

        val unpacked = JsUnpacker(packedJs).unpack().orEmpty()
        if (unpacked.isEmpty()) return

        val m3u8Path =
            Regex("""videoUrl\s*[:=]\s*["'](.*?)["']""")
                .find(unpacked)
                ?.groupValues
                ?.get(1)
                ?: Regex("""["']([^"']+\.(?:m3u8|hls|txt)[^"']*)["']""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)

        if (m3u8Path.isNullOrEmpty()) return

        val cleanPath = m3u8Path.replace("\\/", "/")
        val finalM3u8 =
            if (cleanPath.startsWith("http")) cleanPath
            else "${baseUrl.removeSuffix("/")}/${cleanPath.removePrefix("/")}"

        callback(
            newExtractorLink(
                "Fsplay",
                "Fsplay",
                finalM3u8,
                ExtractorLinkType.M3U8
            ) {
                this.referer = finalUrl
            }
        )
    }
}
