package com.EmbedSports

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlinx.coroutines.delay
import org.json.JSONObject

class EmbedSports : MainAPI() {
    override var mainUrl = "https://embedcanaistv.com"
    override var name = "EmbedSports"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private var postersCache: Map<String, String> = emptyMap()


    private val POSTERS_JSON = """
    {
      "canais": {
        "esportes": [
          {
            "nome": "Amazon Prime Video",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Prime_Video.png",
            "url": "https://embedcanaistv.com/amazonprimevideo"
          },
          {
            "nome": "Band Sports",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/EuPvzJe.png",
            "url": "https://embedcanaistv.com/bandsports/"
          },
          {
            "nome": "Canal GOAT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/F_Jf0NdWAAAa2Ld.png",
            "url": "https://embedcanaistv.com/canalgoat/"
          },
          {
            "nome": "Combate",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/Canal-combate-logo-scaled.png",
            "url": "https://embedcanaistv.com/combate/"
          },
          {
            "nome": "Cazé TV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Perfil-casimito.webp",
            "url": "https://embedcanaistv.com/cazetv/"
          },
          {
            "nome": "DAZN",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/DAZN_BoxedLogo_01_RGB.png",
            "url": "https://embedcanaistv.com/dazn/"
          },
          {
            "nome": "Disney +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/logo-disneyplus-1024.png",
            "url": "https://embedcanaistv.com/disneyplus/"
          },
          {
            "nome": "ESPN",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn/"
          },
          {
            "nome": "ESPN 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn2/"
          },
          {
            "nome": "ESPN 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn3/"
          },
          {
            "nome": "ESPN 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn4/"
          },
          {
            "nome": "ESPN 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn5/"
          },
          {
            "nome": "ESPN 6",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn6/"
          },
          {
            "nome": "Max",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Max_logo.svg_.png",
            "url": "https://embedcanaistv.com/max/"
          },
          {
            "nome": "Nosso Futebol",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/nossofutebol.png",
            "url": "https://embedcanaistv.com/nossofutebol"
          },
          {
            "nome": "Paramount +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/paramount-plus-logo-0-scaled.png",
            "url": "https://embedcanaistv.com/paramountplus"
          },
          {
            "nome": "Premiere Clubes",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiereclubes"
          },
          {
            "nome": "Premiere 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere2"
          },
          {
            "nome": "Premiere 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere3"
          },
          {
            "nome": "Premiere 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere4"
          },
          {
            "nome": "Premiere 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere5"
          },
          {
            "nome": "Premiere 6",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere6"
          },
          {
            "nome": "Premiere 7",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere7"
          },
          {
            "nome": "Premiere 8",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere8"
          },
          {
            "nome": "SportTV Portugal 1",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV1_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal1"
          },
          {
            "nome": "SportTV Portugal 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV2_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal2"
          },
          {
            "nome": "SportTV Portugal 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV3_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal3"
          },
          {
            "nome": "SportTV Portugal 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV4_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal4"
          },
          {
            "nome": "SportTV Portugal 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV5_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal5"
          },
          {
            "nome": "Sportv",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV.png",
            "url": "https://embedcanaistv.com/sportv"
          },
          {
            "nome": "Sportv 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV2.png",
            "url": "https://embedcanaistv.com/sportv2"
          },
          {
            "nome": "Sportv 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV3.png",
            "url": "https://embedcanaistv.com/sportv3"
          },
          {
            "nome": "Star +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Star_logo.svg_.png",
            "url": "https://embedcanaistv.com/starplus"
          },
          {
            "nome": "TNT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/kindpng_5420951.png",
            "url": "https://embedcanaistv.com/tnt"
          },
          {
            "nome": "UFC Fight Pass",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/UFC_Fight_Pass_Logo.svg_.png",
            "url": "https://embedcanaistv.com/ufcfightpass"
          }
        ]
      }
    }
    """.trimIndent()

    override val mainPage = mainPageOf(
        "esportes" to "Esportes"
    )

    private fun loadPostersFromJson(): Map<String, String> {
        if (postersCache.isNotEmpty()) {
            return postersCache
        }

        try {
            val jsonObject = JSONObject(POSTERS_JSON)
            val canais = jsonObject.getJSONObject("canais")
            val posters = mutableMapOf<String, String>()
            
            if (canais.has("esportes")) {
                val categoriaArray = canais.getJSONArray("esportes")
                for (i in 0 until categoriaArray.length()) {
                    val canal = categoriaArray.getJSONObject(i)
                    val nome = canal.getString("nome")
                    val poster = canal.getString("poster")
                    posters[nome] = poster
                }
            }
            
            postersCache = posters
            return posters
            
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    private fun getPosterForChannel(channelName: String): String {
        val posters = loadPostersFromJson()
        val poster = posters[channelName] ?: ""
        
        return if (poster.isNotEmpty()) {
            poster
        } else {
            "https://embedcanaistv.com/wp-content/uploads/2024/11/cropped-cropped-iconn-192x192.png"
        }
    }
    
    private fun createDataUrl(url: String, poster: String): String {
        return "$url|POSTER:$poster"
    }
    
    private fun extractPosterFromDataUrl(dataUrl: String): String? {
        return if (dataUrl.contains("|POSTER:")) {
            dataUrl.split("|POSTER:")[1]
        } else {
            null
        }
    }
    
    private fun extractUrlFromDataUrl(dataUrl: String): String {
        return if (dataUrl.contains("|POSTER:")) {
            dataUrl.split("|POSTER:")[0]
        } else {
            dataUrl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl"
        val response = app.get(url)
        val doc = response.document
        
        val channels = mutableListOf<SearchResponse>()

        val selectedChannels = doc.select("h4:contains(Esportes) + .grid-container .grid-item")

        selectedChannels.forEach { channel ->
            val title = channel.selectFirst("h3")?.text()
            val channelUrl = channel.selectFirst("a")?.attr("href")
            
            if (title != null && channelUrl != null) {
                val posterUrl = getPosterForChannel(title)
                val fullChannelUrl = if (channelUrl.startsWith("http")) channelUrl else "$mainUrl$channelUrl"
                
                channels.add(
                    newTvSeriesSearchResponse(title, createDataUrl(fullChannelUrl, posterUrl), TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return newHomePageResponse(HomePageList(request.name, channels))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl"
        val response = app.get(url)
        val doc = response.document
        
        val results = mutableListOf<SearchResponse>()

        val sportChannels = doc.select("h4:contains(Esportes) + .grid-container .grid-item")

        sportChannels.forEach { channel ->
            val title = channel.selectFirst("h3")?.text()
            val channelUrl = channel.selectFirst("a")?.attr("href")

            if (title != null && channelUrl != null && title.contains(query, ignoreCase = true)) {
                val posterUrl = getPosterForChannel(title)
                val fullChannelUrl = if (channelUrl.startsWith("http")) channelUrl else "$mainUrl$channelUrl"
                
                results.add(
                    newTvSeriesSearchResponse(title, createDataUrl(fullChannelUrl, posterUrl), TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val actualUrl = extractUrlFromDataUrl(url)
        val posterFromData = extractPosterFromDataUrl(url)
        
        val response = app.get(actualUrl)
        val doc = response.document
        
        val title = doc.selectFirst("h1")?.text() ?: doc.title()
        val posterUrl = posterFromData ?: getPosterForChannel(title)

        return newMovieLoadResponse(title, actualUrl, TvType.Live, actualUrl) {
            this.posterUrl = posterUrl
            this.plot = "Canal ao vivo - $title"
            this.dataUrl = actualUrl
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val channelUrl = if (data.isNotEmpty()) extractUrlFromDataUrl(data) else return false
        
        val response = app.get(channelUrl)
        val doc = response.document
        
        val scripts = doc.select("script")
        
        val scriptContent = scripts.find { script ->
            script.data().contains("source:") && script.data().contains(".m3u8")
        }?.data()
        
        if (scriptContent == null) {
            return false
        }

        val m3u8Regex = Regex("source:\\s*\"([^\"]+\\.m3u8[^\"]*)\"")
        val m3u8Match = m3u8Regex.find(scriptContent)
        
        if (m3u8Match != null) {
            val m3u8Url = m3u8Match.groupValues[1]
            
            callback(
                newExtractorLink(
                    "EmbedSports",
                    "EmbedSports Live",
                    m3u8Url
                ) {
                    this.referer = channelUrl
                    this.headers = mapOf(
                        "accept" to "*/*",
                        "accept-encoding" to "gzip, deflate, br, zstd",
                        "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "cache-control" to "no-cache",
                        "dnt" to "1",
                        "origin" to "https://embedcanaistv.com",
                        "pragma" to "no-cache",
                        "referer" to "https://embedcanaistv.com/",
                        "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"Windows\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "sec-gpc" to "1",
                        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    )
                }
            )
            return true
        }

        val alternativeRegex = Regex("embmaxtv\\.online/[^/]+/index\\.m3u8")
        val alternativeMatch = alternativeRegex.find(scriptContent)
        
        if (alternativeMatch != null) {
            val m3u8Url = "https://" + alternativeMatch.value
            
            callback(
                newExtractorLink(
                    "EmbedSports",
                    "EmbedSports Live",
                    m3u8Url
                ) {
                    this.referer = channelUrl
                    this.headers = mapOf(
                        "accept" to "*/*",
                        "accept-encoding" to "gzip, deflate, br, zstd",
                        "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "cache-control" to "no-cache",
                        "dnt" to "1",
                        "origin" to "https://embedcanaistv.com",
                        "pragma" to "no-cache",
                        "referer" to "https://embedcanaistv.com/",
                        "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"Windows\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "sec-gpc" to "1",
                        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    )
                }
            )
            return true
        }

        return false
    }
} 