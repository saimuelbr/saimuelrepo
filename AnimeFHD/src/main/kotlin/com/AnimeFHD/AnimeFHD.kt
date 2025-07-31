package com.AnimeFHD

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.*

class AnimeFHD : MainAPI() {
    override var mainUrl = "https://animefhd.com"
    override var name = "AnimeFHD"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Anime)

    override val mainPage = mainPageOf(
        "?ano=2025" to "2025",
        "?genero=acao" to "Ação",
        "?genero=drama" to "Drama",
        "?genero=militar" to "Militar",
        "?genero=shounen" to "Shounen",
        "?genero=terror" to "Terror",
        "?genero=comedia" to "Comédia",
        "?genero=vida-escolar" to "Vida Escolar",
        "?genero=ecchi" to "Ecchi",
        "?genero=ficcao-cientifica" to "Ficção Científica",
        "?genero=harem" to "Hárem",
        "?genero=romance" to "Romance",
        "?genero=magia" to "Mágia",
        "?genero=isekai" to "Isekai",
        "?genero=psicologico" to "Psicológico",
        "?genero=thriller" to "Thriller",
        "?genero=slice-of-life" to "Slice-of-life",
        "?genero=esportes" to "Esportes",
        "?genero=comedia-romantica" to "Comédia Romântica",
        "?genero=musical" to "Musical"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/lista-de-animes/${request.data}"
        val document = app.get(url).document
        
        val animeElements = document.select("div.ultAnisContainerItem")
        val home = animeElements.mapNotNull { it.toSearchResult() }
        
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("div.aniNome").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("div.aniImg img").attr("src")
        
        if (title.isBlank() || href.isBlank()) {
            return null
        }
        
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.ultAnisContainerItem").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.animeFirstContainer h1, h1.anime-title, h1.title")?.text()?.trim() ?: ""
        
        val poster = document.selectFirst("div.animeCapa img")?.attr("src")
        
        val description = document.selectFirst("div.animeSecondContainer p, .sinopse p, .description p, .plot p")?.text()?.trim()
        
        val genres = document.select("div.animeFirstContainer ul.animeGen li a, .genres a, .tags a, ul.animeGen li a").map { it.text().trim() }.toMutableList()
        
        val episodes = loadEpisodesFromPage(document, url)
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        val episodeInfo = document.selectFirst("div.informacoes_ep")
        val episodeTitle = episodeInfo?.select("div.info")?.find { it.text().contains("Episódio:") }?.text()?.trim()
        val episodeDescription = episodeInfo?.select("div.info")?.find { it.text().contains("Descrição:") }?.text()?.trim()
        val audioType = episodeInfo?.select("div.info")?.find { it.text().contains("Tipo de Áudio:") }?.text()?.trim()
        
        val truncatedDescription = if (episodeDescription != null) {
            val sentences = episodeDescription.split(".")
            if (sentences.size >= 3) {
                sentences.take(3).joinToString(".") + "."
            } else {
                episodeDescription
            }
        } else null
        
        val playerBox = document.selectFirst("div.playerBox iframe")
        if (playerBox == null) {
            return false
        }
        
        val embedUrl = playerBox.attr("src")
        if (embedUrl.isBlank()) {
            return false
        }
        
        val embedDocument = app.get(embedUrl).document
        
        val scripts = embedDocument.select("script")
        var videoUrl = ""
        var quality = ""
        var episodeImage = ""
        
        for (script in scripts) {
            val scriptContent = script.html()
            if (scriptContent.contains("sources") && scriptContent.contains("file")) {
                val fileMatch = Regex("file:\\s*'([^']+)'").find(scriptContent)
                val labelMatch = Regex("label:\\s*\"([^\"]+)\"").find(scriptContent)
                val imageMatch = Regex("image:\\s*'([^']+)'").find(scriptContent)
                
                if (fileMatch != null) {
                    videoUrl = fileMatch.groupValues[1]
                }
                if (labelMatch != null) {
                    quality = labelMatch.groupValues[1]
                }
                if (imageMatch != null) {
                    episodeImage = imageMatch.groupValues[1]
                }
                break
            }
        }
        
        if (videoUrl.isBlank()) {
            return false
        }
        
        val sourceName = buildString {
            append("AnimeFHD")
            if (quality.isNotEmpty()) append(" $quality")
            if (audioType != null) {
                val cleanAudio = audioType.replace("Tipo de Áudio:", "").trim()
                append(" ($cleanAudio)")
            }
            if (episodeTitle != null) {
                val cleanTitle = episodeTitle.replace("Episódio:", "").trim()
                append(" - $cleanTitle")
            }
        }
        
        callback(
            newExtractorLink(
                sourceName,
                sourceName,
                videoUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
            }
        )
        
        return true
    }
    
    private fun loadEpisodesFromPage(document: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        var episodeCounter = 1
        
        val episodeSection = document.selectFirst("div.sectionEpiInAnime#aba_epi")
        if (episodeSection != null) {
            val episodeLinks = episodeSection.select("a.list-epi")
            episodeLinks.forEach { link ->
                val episodeText = link.text().trim()
                val episodeUrl = fixUrl(link.attr("href"))
                
                val episodeMatch = Regex("Episódio\\s*(\\d+)").find(episodeText)
                if (episodeMatch != null) {
                    val episodeNumber = episodeMatch.groupValues[1].toIntOrNull()
                    if (episodeNumber != null) {
                        val episode = newEpisode(episodeUrl) {
                            this.name = "Episódio $episodeNumber"
                            this.episode = episodeNumber
                            this.season = 1
                        }
                        episodes.add(episode)
                        episodeCounter++
                    }
                }
            }
        }
        
        val ovaSection = document.selectFirst("div.sectionEpiInAnime#aba_ova")
        if (ovaSection != null) {
            val ovaLinks = ovaSection.select("a.list-epi")
            ovaLinks.forEach { link ->
                val ovaText = link.text().trim()
                val ovaUrl = fixUrl(link.attr("href"))
                
                val ovaMatch = Regex("Ova\\s*(\\d+)").find(ovaText)
                if (ovaMatch != null) {
                    val ovaNumber = ovaMatch.groupValues[1].toIntOrNull()
                    if (ovaNumber != null) {
                        val episode = newEpisode(ovaUrl) {
                            this.name = "OVA $ovaNumber"
                            this.episode = episodeCounter
                            this.season = 1
                        }
                        episodes.add(episode)
                        episodeCounter++
                    }
                }
            }
        }
        
        val movieSection = document.selectFirst("div.sectionEpiInAnime#aba_movie")
        if (movieSection != null) {
            val movieLinks = movieSection.select("a.list-epi")
            movieLinks.forEach { link ->
                val movieText = link.text().trim()
                val movieUrl = fixUrl(link.attr("href"))
                
                val movieMatch = Regex("Filme\\s*(\\d+)").find(movieText)
                if (movieMatch != null) {
                    val movieNumber = movieMatch.groupValues[1].toIntOrNull()
                    if (movieNumber != null) {
                        val episode = newEpisode(movieUrl) {
                            this.name = "Filme $movieNumber"
                            this.episode = episodeCounter
                            this.season = 1
                        }
                        episodes.add(episode)
                        episodeCounter++
                    }
                }
            }
        }
        
        return episodes.sortedBy { it.episode }
    }
} 