package com.GoFlix

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper

class GoFlixExtractor : ExtractorApi() {
    override val name = "GoFlix"
    override val mainUrl = "https://goflix3.lol"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        extractWithWebView(url, referer, subtitleCallback, callback)
    }

    private suspend fun extractWithWebView(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val webViewResolver = WebViewResolver(
                interceptUrl = Regex("(https?://[^\"'\\s]+\\.(?:m3u8|master))"),
                additionalUrls = listOf(Regex("(https?://[^\"'\\s]+\\.(?:m3u8|master))")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val intercepted = app.get(
                url, 
                referer = referer,
                interceptor = webViewResolver,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                    "Accept-Language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                    "Cache-Control" to "no-cache",
                    "Pragma" to "no-cache",
                    "Origin" to mainUrl,
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin"
                )
            ).url

            if (intercepted.isNotEmpty() && 
                (intercepted.contains(".m3u8") || intercepted.contains("master")) && 
                !intercepted.endsWith(".txt") && 
                !intercepted.contains("/e/")) {
                
                M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = intercepted,
                    referer = referer ?: url,
                    headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to (referer ?: url),
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                ).forEach(callback)
            }
        } catch (e: Exception) {
        }
    }

}
