package com.DonghuaNoSekai

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

object DonghuaNoSekaiExtractor {
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""m3u8"""),
                additionalUrls = listOf(Regex("""m3u8""")),
                useOkhttp = false,
                timeout = 15_000L
            )
            
            val intercepted = app.get(url, interceptor = m3u8Resolver).url
            
            if (intercepted.isNotEmpty() && intercepted.contains(".m3u8")) {
                val m3u8Url = if (intercepted.contains("player-nativov2.php?v=")) {
                    val m3u8Match = Regex("""v=([^&]+)""").find(intercepted)
                    m3u8Match?.groupValues?.get(1) ?: intercepted
                } else {
                    intercepted
                }
                
                val headers = mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site",
                    "Referer" to url,
                    "Origin" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                
                M3u8Helper.generateM3u8(
                    name,
                    m3u8Url,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
                
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
