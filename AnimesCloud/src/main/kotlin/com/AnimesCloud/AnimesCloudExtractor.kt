package com.AnimesCloud

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.VidStack
import java.net.URLDecoder

class AnimesHD : VidStack() {
    override var name = "AnimesHD" 
    override var mainUrl = "https://animeshd.cloud"
    override var requiresReferer = true
}

class AnimesSTRP : VidStack() {
    override var name = "Animes STRP" 
    override var mainUrl = "https://animes.strp2p.com"
    override var requiresReferer = true
}

object AnimesCloudExtractor {
    private val cfKiller = CloudflareKiller()
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(url, interceptor = cfKiller, headers = mapOf("User-Agent" to USER_AGENT)).document
            val playerOptions = document.select("ul#playeroptionsul li.dooplay_player_option")
            
            if (playerOptions.isEmpty()) return false
            var hasValidLinks = false
            
            val priorityLinks = mutableListOf<suspend () -> Unit>()
            val lateLinks = mutableListOf<suspend () -> Unit>()

            for (playerOption in playerOptions) {
                val dataType = playerOption.attr("data-type")
                val dataPost = playerOption.attr("data-post")
                val dataNume = playerOption.attr("data-nume")
                val title = playerOption.select("span.title").text().trim()
                
                if (title.contains("Mobile", true) || title.contains("Celular", true)) continue

                if (dataType.isNotEmpty() && dataPost.isNotEmpty() && dataNume.isNotEmpty()) {
                    val ajaxUrl = "$mainUrl/wp-json/dooplayer/v2/$dataPost/$dataType/$dataNume"
                    
                    try {
                        val ajaxResponse = app.get(ajaxUrl, interceptor = cfKiller, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url)).text
                        val embedUrlMatch = Regex("\"embed_url\":\"([^\"]+)\"").find(ajaxResponse)
                        
                        if (embedUrlMatch != null) {
                            val embedUrl = embedUrlMatch.groupValues[1].replace("\\/", "/").replace("\\", "")
                            
                            val action: suspend () -> Unit = {
                                if (embedUrl.contains("source=") && (embedUrl.contains(".mp4") || embedUrl.contains(".m3u8"))) {
                                    Regex("source=([^&]+)").find(embedUrl)?.groupValues?.get(1)?.let {
                                        val directUrl = URLDecoder.decode(it, "UTF-8")
                                        callback(newExtractorLink("AnimesCloud $title", "AnimesCloud $title", directUrl, ExtractorLinkType.VIDEO) { this.referer = mainUrl })
                                        hasValidLinks = true
                                    }
                                } else if (embedUrl.contains("animeshd.cloud")) {
                                    val extractor = AnimesHD()
                                    extractor.name = "AnimesCloud $title"
                                    extractor.getUrl(embedUrl, mainUrl, { }, callback)
                                    hasValidLinks = true
                                } else if (embedUrl.contains("animes.strp2p.com")) {
                                    val extractor = AnimesSTRP()
                                    extractor.name = "AnimesCloud $title"
                                    extractor.getUrl(embedUrl, mainUrl, { }, callback)
                                    hasValidLinks = true
                                }
                            }

                            if (title.contains("HLS", true) || title.contains("FullHD", true)) {
                                lateLinks.add(action)
                            } else {
                                priorityLinks.add(action)
                            }
                        }
                    } catch (_: Exception) { continue }
                }
            }

            priorityLinks.forEach { it.invoke() }
            lateLinks.forEach { it.invoke() }
            hasValidLinks
        } catch (e: Exception) { false }
    }
}