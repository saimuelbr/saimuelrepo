package com.BakashiTV

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.network.WebViewResolver

object BakashiTVExtractor {
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(url).document
            val playerOption = document.selectFirst("li.dooplay_player_option[data-post][data-type][data-nume]")
            
            if (playerOption != null) {
                val dataPost = playerOption.attr("data-post")
                val dataType = playerOption.attr("data-type")
                val dataNume = playerOption.attr("data-nume")
                
                val apiUrl = "https://bakashi.tv/wp-json/dooplayer/v2/$dataPost/$dataType/$dataNume"
                val apiResponse = app.get(apiUrl).text
                
                val embedUrlMatch = Regex("""\"embed_url\":\s*\"([^\"]+)\"""").find(apiResponse)
                if (embedUrlMatch != null) {
                    val embedUrl = embedUrlMatch.groupValues[1].replace("\\/", "/")
                    return processEmbedWithWebView(embedUrl, name, callback)
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun processEmbedWithWebView(
        embedUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""=video/mp4|\.mp4|\.m3u8"""),
                additionalUrls = listOf(Regex("""=video/mp4|\.mp4|\.m3u8""")),
                useOkhttp = false,
                timeout = 15_000L
            )
            
            val intercepted = app.get(embedUrl, interceptor = m3u8Resolver).url
            
            if (intercepted.isNotEmpty() && (intercepted.contains("=video/mp4") || intercepted.contains(".mp4") || intercepted.contains(".m3u8"))) {
                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name",
                        intercepted,
                        INFER_TYPE
                    ) {
                        this.referer = "https://bakashi.tv"
                    }
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
