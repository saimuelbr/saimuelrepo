package com.AnimesOnlineNet

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Document
import com.lagradost.api.Log

object AnimesOnlineNetExtractor {
    
    suspend fun extractVideoLinks(
        url: String,
        mainUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("AnimesOnlineNet", "Starting extraction for URL: $url")
        return try {
            val document = app.get(url).document
            val playerOption = document.selectFirst("li.dooplay_player_option[data-post][data-type][data-nume]")
            
            if (playerOption != null) {
                val dataPost = playerOption.attr("data-post")
                val dataType = playerOption.attr("data-type")
                val dataNume = playerOption.attr("data-nume")
                
                Log.d("AnimesOnlineNet", "Found player option - post: $dataPost, type: $dataType, nume: $dataNume")
                
                val apiUrl = "https://animesonlinenet.com/wp-json/dooplayer/v2/$dataPost/$dataType/$dataNume"
                Log.d("AnimesOnlineNet", "Calling dooplayer API: $apiUrl")
                
                val apiResponse = app.get(apiUrl).text
                Log.d("AnimesOnlineNet", "Dooplayer API response length: ${apiResponse.length}")
                
                val embedUrlMatch = Regex("""\"embed_url\":\s*\"([^\"]+)\"""").find(apiResponse)
                if (embedUrlMatch != null) {
                    val embedUrl = embedUrlMatch.groupValues[1].replace("\\/", "/")
                    Log.d("AnimesOnlineNet", "Found embed URL: $embedUrl")
                    return processEmbedWithNewLogic(embedUrl, name, callback)
                } else {
                    Log.d("AnimesOnlineNet", "No embed_url found in dooplayer response")
                }
            } else {
                Log.d("AnimesOnlineNet", "No player option found on page")
            }
            
            false
        } catch (e: Exception) {
            Log.e("AnimesOnlineNet", "Error in extractVideoLinks: ${e.message}")
            false
        }
    }
    
    private suspend fun processEmbedWithNewLogic(
        embedUrl: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("AnimesOnlineNet", "Processing embed URL: $embedUrl")
        return try {
            val embedDocument = app.get(embedUrl).document
            Log.d("AnimesOnlineNet", "Got embed document, searching for file in scripts")
            
            val fileMatch = findFileInScripts(embedDocument)
            if (fileMatch != null) {
                Log.d("AnimesOnlineNet", "Found file in scripts: $fileMatch")
                
                val apiUrl = "https://api.myblogapi.site/api/v1/decode/blogg/$fileMatch"
                Log.d("AnimesOnlineNet", "Calling decode API: $apiUrl")
                
                val apiResponse = app.get(apiUrl).parsed<Map<String, Any>>()
                Log.d("AnimesOnlineNet", "Decode API response: $apiResponse")
                
                if (apiResponse["status"] == "success") {
                    val playArray = apiResponse["play"] as? List<Map<String, Any>>
                    if (playArray != null) {
                        Log.d("AnimesOnlineNet", "Found ${playArray.size} video sources")
                        var hasValidLinks = false
                        
                        for (video in playArray) {
                            val src = video["src"] as? String
                            val sizeText = video["sizeText"] as? String
                            
                            Log.d("AnimesOnlineNet", "Processing video - src: ${src?.take(50)}..., size: $sizeText")
                            
                            if (src != null && sizeText != null) {
                                val qualityName = "$name - $sizeText"
                                Log.d("AnimesOnlineNet", "Creating link: $qualityName")
                                
                                callback.invoke(
                                    newExtractorLink(
                                        qualityName,
                                        qualityName,
                                        src,
                                        INFER_TYPE
                                    ) {
                                        this.referer = "https://bakashi.tv"
                                    }
                                )
                                hasValidLinks = true
                            }
                        }
                        
                        Log.d("AnimesOnlineNet", "Total valid links created: $hasValidLinks")
                        return hasValidLinks
                    } else {
                        Log.d("AnimesOnlineNet", "No play array found in response")
                    }
                } else {
                    Log.d("AnimesOnlineNet", "API response status is not success: ${apiResponse["status"]}")
                }
            } else {
                Log.d("AnimesOnlineNet", "No file found in embed scripts")
            }
            
            false
        } catch (e: Exception) {
            Log.e("AnimesOnlineNet", "Error in processEmbedWithNewLogic: ${e.message}")
            false
        }
    }
    
    private fun findFileInScripts(document: Document): String? {
        val scripts = document.select("script")
        Log.d("AnimesOnlineNet", "Searching in ${scripts.size} scripts for file pattern")
        
        for ((index, script) in scripts.withIndex()) {
            val scriptContent = script.html()
            Log.d("AnimesOnlineNet", "Script $index length: ${scriptContent.length}")
            
            val fileMatch = Regex("""\"file\":\s*\"([A-Za-z0-9+/=]+)\"""").find(scriptContent)
            if (fileMatch != null) {
                Log.d("AnimesOnlineNet", "Found file match in script $index: ${fileMatch.groupValues[1].take(20)}...")
                return fileMatch.groupValues[1]
            }
        }
        
        Log.d("AnimesOnlineNet", "No file pattern found in any script")
        return null
    }
}
