package com.BetterAnime

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.nodes.Document

object BetterAnimeExtractor {
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(url).document
            val playerOption = document.selectFirst("li.dooplay_player_option[data-post][data-type][data-nume]")
                ?: return false
            
            val dataPost = playerOption.attr("data-post")
            val dataType = playerOption.attr("data-type")
            val dataNume = playerOption.attr("data-nume")
            
            val apiUrl = "https://betteranime.io/wp-json/dooplayer/v2/$dataPost/$dataType/$dataNume"
            val apiResponse = app.get(apiUrl).text
            
            val embedUrlMatch = Regex("""\"embed_url\":\s*\"([^\"]+)\"""").find(apiResponse)
            if (embedUrlMatch != null) {
                val embedUrl = embedUrlMatch.groupValues[1].replace("\\/", "/")
                processEmbedWithNewLogic(embedUrl, name, callback)
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun processEmbedWithNewLogic(
        embedUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val embedDocument = app.get(embedUrl).document
            val fileMatch = findFileInScripts(embedDocument) ?: return false
            
            val apiUrl = "https://api.myblogapi.site/api/v1/decode/blogg/$fileMatch"
            val apiResponse = app.get(apiUrl).parsed<Map<String, Any>>()
            
            if (apiResponse["status"] == "success") {
                val playArray = apiResponse["play"] as? List<Map<String, Any>> ?: return false
                var hasValidLinks = false
                
                val sortedPlayArray = playArray.sortedByDescending { 
                    (it["sizeText"] as? String)?.contains("HD", ignoreCase = true) ?: false 
                }

                for (video in sortedPlayArray) { 
                    val src = video["src"] as? String
                    val sizeText = video["sizeText"] as? String
                    
                    if (src != null && sizeText != null) {
                        val qualityName = "$name - $sizeText"
                        callback.invoke(
                            newExtractorLink(
                                qualityName,
                                qualityName,
                                src,
                                INFER_TYPE
                            ) {
                                this.referer = "https://betteranime.io"
                            }
                        )
                        hasValidLinks = true
                    }
                }
                hasValidLinks
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun findFileInScripts(document: Document): String? {
        return document.select("script").map { it.html() }.firstNotNullOfOrNull { content ->
            Regex("""\"file\":\s*\"([A-Za-z0-9+/=]+)\"""").find(content)?.groupValues?.get(1)
        }
    }
}