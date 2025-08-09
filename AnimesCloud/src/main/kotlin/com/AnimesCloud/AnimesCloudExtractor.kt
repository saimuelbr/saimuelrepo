package com.AnimesCloud

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import org.jsoup.nodes.Document


object AnimesCloudExtractor {
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {

            
            val document = app.get(url).document

            
            val playerOptions = document.select("ul#playeroptionsul li.dooplay_player_option")
            
            if (playerOptions.isEmpty()) {

                return false
            }
            
            var hasValidLinks = false
            
            for ((index, playerOption) in playerOptions.withIndex()) {
                
                val dataType = playerOption.attr("data-type")
                val dataPost = playerOption.attr("data-post")
                val dataNume = playerOption.attr("data-nume")
                val title = playerOption.select("span.title").text().trim()
                

                
                if (dataType.isNotEmpty() && dataPost.isNotEmpty() && dataNume.isNotEmpty()) {
                    val ajaxUrl = "$mainUrl/wp-json/dooplayer/v2/$dataPost/$dataType/$dataNume"
                    
                    try {
                        val ajaxResponse = app.get(ajaxUrl).text
                        
                        val embedUrlMatch = Regex("\"embed_url\":\"([^\"]+)\"").find(ajaxResponse)
                        if (embedUrlMatch != null) {
                            val embedUrl = embedUrlMatch.groupValues[1]
                                .replace("\\/", "/")
                                .replace("\\", "")
                            
                            if (embedUrl.contains("animes.strp2p.com") || embedUrl.contains("youtube.com/embed/pIb3zixhP3A") || embedUrl.contains("animeshd.cloud/#cvgohd")) {
                                continue
                            }
                            

                            
                            val webViewResult = tryWebViewResolver(embedUrl, mainUrl, title, dataType, callback)
                            if (webViewResult) {
                                hasValidLinks = true
                            }
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            hasValidLinks
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun tryWebViewResolver(
        url: String,
        mainUrl: String,
        title: String,
        dataType: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {

            val sourceRegex = Regex("""source=([^"']+\.mp4)""")
            val sourceMatch = sourceRegex.find(url)

            if (sourceMatch != null) {
                val encodedMp4Url = sourceMatch.groupValues[1]
                val mp4Url = java.net.URLDecoder.decode(encodedMp4Url, "UTF-8")

                val sourceName = buildString {
                    append("AnimesCloud")
                    if (title.isNotEmpty()) append(" $title")
                    if (dataType == "movie") append(" $title")
                }

                callback(
                    newExtractorLink(
                        sourceName,
                        sourceName,
                        mp4Url,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                    }
                )

                return true
            }

            val videoResolver = WebViewResolver(
                interceptUrl = Regex("\\.mp4$"),
                additionalUrls = listOf(Regex("\\.mp4$")),
                useOkhttp = false,
                timeout = 25_000L
            )

            val intercepted = app.get(url, interceptor = videoResolver).url

            if (intercepted.isNotEmpty()) {
                val sourceName = buildString {
                    append("AnimesCloud")
                    if (title.isNotEmpty()) append(" $title")
                    if (dataType == "movie") append(" (Filme)")
                }

                if (intercepted.endsWith(".mp4")) {

                    callback(
                        newExtractorLink(
                            sourceName,
                            sourceName,
                            intercepted,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                        }
                    )

                    return true
                }
            }

            false
        } catch (e: Exception) {
            false
        }
    }
}