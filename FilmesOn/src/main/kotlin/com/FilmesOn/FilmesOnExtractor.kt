package com.FilmesOn

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder

class FilmesOnExtractor(private val mainUrl: String, val name: String) {

    suspend fun processPlayerOptions(
        playerOptions: List<Element>,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLink = false
        playerOptions.forEach { option ->
            val server = option.select("span.server").text().trim()
            if (server.contains("tudoverhd.online", true) || 
                server.contains("azullog.site", true) || 
                server.contains("FHD", true)) {
                
                requestEmbedUrl(option, referer)?.let {
                    processEmbedPage(it, callback)
                    foundLink = true
                }
            }
        }
        return foundLink
    }

    private suspend fun requestEmbedUrl(option: Element, referer: String): String? {
        val domain = try {
            val url = java.net.URL(referer)
            "${url.protocol}://${url.host}"
        } catch (e: Exception) { mainUrl }

        val headers = mapOf(
            "x-requested-with" to "XMLHttpRequest",
            "referer" to referer,
            "origin" to domain,
            "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        )

        val payload = mapOf(
            "action" to "doo_player_ajax",
            "post" to option.attr("data-post"),
            "nume" to option.attr("data-nume"),
            "type" to option.attr("data-type")
        )

        return try {
            val response = app.post("$domain/wp-admin/admin-ajax.php", headers = headers, data = payload).text
            if (response == "0" || response.isBlank()) return null
            Regex("""(?i)"embed_url"\s*:\s*"([^"]+)""").find(response)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: Jsoup.parse(response).select("iframe").attr("src")
        } catch (e: Exception) { null }
    }

    private suspend fun processEmbedPage(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            app.get(url).document.select("div.player_select_item").forEach { option ->
                val embedData = option.attr("data-embed").let {
                    if (it.contains("filecdn")) it.replace(Regex("filecdn\\d*\\.site"), "1take.lat") else it
                }
                val prefix = option.select(".player_select_name").text().trim()
                
                if (embedData.isNotEmpty()) {
                    val html = app.get(embedData, headers = mapOf("referer" to url)).text
                    Jsoup.parse(html).select("iframe[src*='player_2.php']").attr("src").takeIf { it.isNotEmpty() }?.let {
                        handleFinalStep(if (it.startsWith("//")) "https:$it" else it, embedData, prefix, callback)
                    }
                }
            }
        } catch (e: Exception) { }
    }

    private suspend fun handleFinalStep(playerUrl: String, referer: String, prefix: String, callback: (ExtractorLink) -> Unit) {
        try {
            val html = app.get(playerUrl, headers = mapOf("referer" to referer)).text
            val apiUrl = Regex("const apiUrl = `([^`]+)`").find(html)?.groupValues?.get(1) ?: return
            val mediafireUrl = Regex("[?&]url=([^&]+)").find(apiUrl)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: return

            val direct = app.get(mediafireUrl).document.select("a#downloadButton").attr("href")
            if (direct.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "$prefix - ${this.name}",
                        url = direct,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = Qualities.P1080.value
                        this.referer = "https://www.mediafire.com/"
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
                            "Referer" to mediafireUrl
                        )
                    }
                )
            }
        } catch (e: Exception) { }
    }
}