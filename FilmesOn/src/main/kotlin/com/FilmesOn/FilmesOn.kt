package com.FilmesOn

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import com.lagradost.api.Log
import java.net.URLDecoder
import com.lagradost.cloudstream3.toRatingInt

class FilmesOn : MainAPI() {
    override var mainUrl = "https://filmeson1.site"
    override var name = "FilmesOn"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    
    override val mainPage = mainPageOf(
        "genero/acao" to "Ação",
        "genero/aventura" to "Aventura",
        "genero/ficcao-cientifica" to "Ficção Científica",
        "genero/comedia" to "Comédia",
        "genero/drama" to "Drama",
        "genero/terror" to "Terror",
        "genero/romance" to "Romance",
        "genero/animacao" to "Animação",
        "genero/documentario" to "Documentário",
        "genero/crime" to "Crime",
        "genero/misterio" to "Mistério",
        "genero/familia" to "Família",
        "genero/fantasia" to "Fantasia",
        "genero/guerra" to "Guerra",
        "genero/historia" to "História",
        "genero/musica" to "Música",
        "genero/thriller" to "Thriller"
    )

    // MARK: - Main Page
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = buildMainPageUrl(request.data, page)
        val document = app.get(url).document
        
        val home = document.selectMainPageItems()
        val hasNext = document.hasNextPage()
        
        return newHomePageResponse(
            name = request.name,
            list = home,
            hasNext = hasNext
        )
    }

    // MARK: - Search
    override suspend fun search(query: String): List<SearchResponse> {
        val url = buildSearchUrl(query)
        val document = app.get(url).document
        
        val results = mutableListOf<SearchResponse>()
        document.select("div.result-item article").forEach { el ->
            val baseTitle = el.select("div.details div.title a").text().trim()
            val href = fixUrl(el.select("div.details div.title a").attr("href"))
            val poster = el.select("div.image img").attr("src").replace("/w92/", "/original/")
            val year = el.select("div.meta span.year").text().trim().toIntOrNull()
            val isSeries = el.select("span.tvshows").isNotEmpty() || href.contains("/series/")
            
            val ratingText = el.select("div.meta span.rating").text().trim()
            val number = Regex("(\\d+[\\.,]?\\d*)").find(ratingText)?.groupValues?.get(1)
            val normalized = number?.replace(",", ".")
            val displayTitle = if (!normalized.isNullOrBlank()) "$baseTitle • IMDb $normalized" else baseTitle
            
            val resp = if (isSeries) {
                newTvSeriesSearchResponse(displayTitle, href, TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = year
                }
            } else {
                newMovieSearchResponse(displayTitle, href, TvType.Movie) {
                    this.posterUrl = poster
                    this.year = year
                }
            }
            results += resp
        }
        
        return results
    }

    // MARK: - Load Content
    override suspend fun load(url: String): LoadResponse {
        Log.d("FilmesOn", "Loading content from: $url")
        val document = app.get(url).document
        
        val contentInfo = document.extractContentInfo()
        val isSeries = url.contains("/series/")
        
        Log.d("FilmesOn", "Content info: title=${contentInfo.title}, year=${contentInfo.year}, rating=${contentInfo.rating}, genres=${contentInfo.genres}")
        
        return if (isSeries) {
            buildSeriesResponse(url, contentInfo, document)
        } else {
            buildMovieResponse(url, contentInfo)
        }
    }

    // MARK: - Load Links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FilmesOn", "Loading links from: $data")
        val document = app.get(data).document
        val playerOptions = document.selectPlayerOptions()
        
        Log.d("FilmesOn", "Found ${playerOptions.size} player options")
        
        return processPlayerOptions(playerOptions, data, subtitleCallback, callback)
    }

    // MARK: - Private Helper Methods
    
    private fun buildMainPageUrl(data: String, page: Int): String {
        return "$mainUrl/$data${if (page > 1) "/page/$page/" else "/"}"
    }
    
    private fun buildSearchUrl(query: String): String {
        return "$mainUrl/?s=${query.replace(" ", "+")}"
    }
    
    private fun Element.selectMainPageItems(): List<SearchResponse> {
        return select("div.items.full article.item")
            .mapNotNull { it.toSearchResult() }
    }
    
    private fun Element.hasNextPage(): Boolean {
        return select("div.pagination a.arrow_pag").isNotEmpty()
    }
    
    private fun Element.selectSearchResults(): List<SearchResponse> {
        return select("div.result-item article")
            .mapNotNull { it.toSearchResult() }
    }
    
    private fun Element.toSearchResult(): SearchResponse? {
        val title = extractTitle()
        if (title.isBlank()) return null
        
        val href = extractHref()
        val posterUrl = extractPosterUrl()
        val isSeries = isSeriesContent(href)
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    private fun Element.extractTitle(): String {
        return select("div.data h3 a, div.details div.title a").text().trim()
    }
    
    private fun Element.extractHref(): String {
        return fixUrl(select("div.data h3 a, div.details div.title a").attr("href"))
    }
    
    private fun Element.extractPosterUrl(): String {
        return select("div.poster img, div.image img").attr("src")
            .replace("w185", "original")
            .replace("w92", "original")
    }
    
    private fun Element.isSeriesContent(href: String): Boolean {
        return hasClass("tvshows") || href.contains("/series/") || select("span.tvshows").isNotEmpty()
    }
    
    private fun Element.extractContentInfo(): ContentInfo {
        return ContentInfo(
            title = extractContentTitle(),
            poster = extractContentPoster(),
            background = extractContentPoster(),
            genres = extractGenres(),
            tags = extractTags(),
            year = extractYear(),
            plot = extractPlot(),
            rating = extractRating(),
            duration = extractDuration(),
            actors = extractActors(),
            directors = extractDirectors()
        )
    }
    
    private fun Element.extractContentTitle(): String {
        return selectFirst("h1")?.text()?.trim() ?: ""
    }
    
    private fun Element.extractContentPoster(): String? {
        val posterRegex = Regex("https://image\\.tmdb\\.org/t/p/[^\"]+")
        return posterRegex.find(html())?.value?.replace("w300", "original")
    }
    
    private fun Element.extractGenres(): List<String> {
        return select("div.sgeneros a").map { it.text().trim() }
    }
    
    private fun Element.extractTags(): List<String> {
        val tags = mutableListOf<String>()
        
        select("div.extra span").forEach { span ->
            val text = span.text().trim()
            when {
                text.matches(Regex("[A-Z]+-\\d+")) -> {
                    tags.add(text)
                }
            }
        }
        
        return tags
    }
    
    private fun Element.extractYear(): Int? {
        val dateText = select("div.extra span.date").text().trim()
        Log.d("FilmesOn", "Date text: '$dateText'")
        
        // Try to extract year from date text like "Jul. 09, 2025"
        val yearMatch = Regex("(\\d{4})").find(dateText)
        if (yearMatch != null) {
            val year = yearMatch.groupValues[1].toIntOrNull()
            Log.d("FilmesOn", "Extracted year: $year")
            return year
        }
        
        return null
    }
    
    private fun Element.extractPlot(): String {
        return select("div.wp-content blockquote p").text().trim()
    }
    
    private fun Element.extractRating(): String? {
        val imdb = select("div.custom_fields")
            .firstOrNull { it.select("b.variante").text().contains("Avaliação IMDb") }
            ?.select("span.valor strong")?.text()?.trim()
        val tmdb = select("div.custom_fields")
            .firstOrNull { it.select("b.variante").text().contains("Avaliação TMDb") }
            ?.select("span.valor strong")?.text()?.trim()
        val rating = imdb ?: tmdb
        Log.d("FilmesOn", "Extracted rating text: $rating (IMDb: $imdb, TMDb: $tmdb)")
        return rating
    }
    
    private fun Element.extractDuration(): Int? {
        // Movie runtime: <span class="runtime">129 Min.</span>
        val movieText = select("div.extra span.runtime").text().trim()
        val movieMinutes = Regex("(\\d+)").find(movieText)?.groupValues?.get(1)?.toIntOrNull()
        if (movieMinutes != null) return movieMinutes
        
        // Series average duration: custom_fields > Average Duration : "50 minutes"
        val seriesDurationText = select("div.custom_fields")
            .firstOrNull { it.select("b.variante").text().contains("Average Duration", ignoreCase = true) }
            ?.select("span.valor")?.text()?.trim()
        val seriesMinutes = Regex("(\\d+)").find(seriesDurationText ?: "")?.groupValues?.get(1)?.toIntOrNull()
        return seriesMinutes
    }
    
    private fun Element.extractActors(): List<Actor> {
        val actors = mutableListOf<Actor>()
        
        select("div#cast div.persons div.person").forEach { person ->
            val name = person.select("div.data div.name a").text().trim()
            val role = person.select("div.data div.caracter").text().trim()
            val image = person.select("div.img img").attr("src")
            
            if (!role.equals("Director", ignoreCase = true)) {
                actors.add(Actor(name, image))
            }
        }
        
        return actors
    }
    
    private fun Element.extractDirectors(): List<String> {
        return select("div#cast div.persons div.person")
            .filter { it.select("div.data div.caracter").text().equals("Director", ignoreCase = true) }
            .map { it.select("div.data div.name a").text().trim() }
    }
    
    private suspend fun buildSeriesResponse(
        url: String,
        contentInfo: ContentInfo,
        document: Element
    ): LoadResponse {
        val episodes = document.extractEpisodes()
        
        return newTvSeriesLoadResponse(contentInfo.title, url, TvType.TvSeries, episodes) {
            this.posterUrl = contentInfo.poster
            this.backgroundPosterUrl = contentInfo.background
            this.year = contentInfo.year
            this.plot = contentInfo.plot
            this.rating = contentInfo.rating?.toRatingInt()
            this.duration = contentInfo.duration
            this.tags = contentInfo.genres + contentInfo.tags
            addActors(contentInfo.actors)
        }
    }
    
    private suspend fun buildMovieResponse(
        url: String,
        contentInfo: ContentInfo
    ): LoadResponse {
        return newMovieLoadResponse(contentInfo.title, url, TvType.Movie, url) {
            this.posterUrl = contentInfo.poster
            this.backgroundPosterUrl = contentInfo.background
            this.year = contentInfo.year
            this.plot = contentInfo.plot
            this.rating = contentInfo.rating?.toRatingInt()
            this.duration = contentInfo.duration
            this.tags = contentInfo.genres + contentInfo.tags
            addActors(contentInfo.actors)
        }
    }
    
    private fun Element.extractEpisodes(): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        select("div#serie_contenido div.se-c").forEach { seasonElement ->
            val seasonNumber = seasonElement.select("span.se-t").text().toIntOrNull() ?: 1
            
            seasonElement.select("ul.episodios li").forEach { episodeElement ->
                val episodeNumber = episodeElement.select("div.numerando").text()
                    .split(" - ").lastOrNull()?.toIntOrNull() ?: 1
                val episodeTitle = episodeElement.select("div.episodiotitle a").text().trim()
                val episodeUrl = fixUrl(episodeElement.select("div.episodiotitle a").attr("href"))
                
                // FilmesOn-specific poster: tmdb w154 -> original, with simple fallbacks
                val imagenDiv = episodeElement.selectFirst("div.imagen")
                val imgEl = imagenDiv?.selectFirst("img")
                var epPoster = imgEl?.attr("src")
                if (!epPoster.isNullOrBlank()) {
                    epPoster = epPoster.replace("/w154/", "/original/")
                }
                if (epPoster.isNullOrBlank() || epPoster.startsWith("data:image")) {
                    val lazy = imgEl?.attr("data-lazy-src")
                    if (!lazy.isNullOrBlank()) epPoster = lazy.replace("/w154/", "/original/")
                }
                if (epPoster.isNullOrBlank() || epPoster.startsWith("data:image")) {
                    val noscriptImg = imagenDiv?.selectFirst("noscript")?.selectFirst("img")
                    val ns = noscriptImg?.attr("src")
                    if (!ns.isNullOrBlank()) epPoster = ns.replace("/w154/", "/original/")
                }
                val finalPoster = epPoster?.let { fixUrl(it) }
                
                // Air date from episode tile span.date (e.g., "Oct. 01, 2006")
                val airDate = episodeElement.select("div.episodiotitle span.date").text().trim().ifBlank { null }
                
                val ep = newEpisode(episodeUrl) {
                    this.name = episodeTitle
                    this.season = seasonNumber
                    this.episode = episodeNumber
                    this.posterUrl = finalPoster
                }
                if (!airDate.isNullOrBlank()) {
                    ep.addDate(airDate)
                }
                episodes += ep
            }
        }
        
        return episodes
    }
    
    private fun Element.selectPlayerOptions(): List<Element> {
        return select("ul#playeroptionsul li.dooplay_player_option")
    }
    
    private suspend fun processPlayerOptions(
        playerOptions: List<Element>,
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        for (option in playerOptions) {
            val server = option.select("span.server").text().trim()
            Log.d("FilmesOn", "Processing player option with server: $server")
            
            if (server == "tudoverhd.online") {
                Log.d("FilmesOn", "Found tudoverhd.online server, requesting embed URL")
                val embedUrl = requestEmbedUrl(option, data)
                if (embedUrl != null) {
                    Log.d("FilmesOn", "Got embed URL: $embedUrl")
                    processEmbedPage(embedUrl, subtitleCallback, callback)
                } else {
                    Log.d("FilmesOn", "Failed to get embed URL")
                }
            }
        }
        return true
    }
    
    private suspend fun requestEmbedUrl(option: Element, data: String): String? {
        val postId = option.attr("data-post")
        val type = option.attr("data-type")
        val nume = option.attr("data-nume")
        
        Log.d("FilmesOn", "AJAX request params: postId=$postId, type=$type, nume=$nume")
        
        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        val headers = buildAjaxHeaders(data)
        val payload = buildAjaxPayload(postId, nume, type)
        
        return try {
            Log.d("FilmesOn", "Making AJAX request to: $ajaxUrl")
            val response = app.post(ajaxUrl, headers = headers, data = payload).text
            Log.d("FilmesOn", "AJAX response: $response")
            val embedUrl = extractEmbedUrl(response)
            Log.d("FilmesOn", "Extracted embed URL: $embedUrl")
            embedUrl
        } catch (e: Exception) {
            Log.d("FilmesOn", "AJAX request failed: ${e.message}")
            null
        }
    }
    
    private fun buildAjaxHeaders(data: String): Map<String, String> {
        return mapOf(
            "x-requested-with" to "XMLHttpRequest",
            "referer" to data,
            "origin" to mainUrl,
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )
    }
    
    private fun buildAjaxPayload(postId: String, nume: String, type: String): Map<String, String> {
        return mapOf(
            "action" to "doo_player_ajax",
            "post" to postId,
            "nume" to nume,
            "type" to type
        )
    }
    
    private fun extractEmbedUrl(response: String): String? {
        return Regex("\"embed_url\":\"([^\"]+)\"")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.replace("\\/", "/")
    }
    
    private suspend fun processEmbedPage(
        embedUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("FilmesOn", "Processing embed page: $embedUrl")
        val document = app.get(embedUrl).document
        val videoOptions = document.select("div.player_select_item")
        
        Log.d("FilmesOn", "Found ${videoOptions.size} video options")
        
        for (option in videoOptions) {
            val embedData = option.attr("data-embed")
            val videoType = option.select("div.player_select_name").text().trim()
            
            Log.d("FilmesOn", "Processing video option: $videoType, embedData: $embedData")
            
            if (embedData.isNotEmpty()) {
                processVideoEmbed(embedData, videoType, subtitleCallback, callback)
            }
        }
    }
    
    private suspend fun processVideoEmbed(
        embedData: String,
        videoType: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("FilmesOn", "Processing video embed: $embedData")
        val document = app.get(embedData).document
        val iframeSrc = document.select("iframe[src*='1take.lat/player']").attr("src")
        Log.d("FilmesOn", "Found iframe src: $iframeSrc")
        if (iframeSrc.isEmpty()) return
        // Request player page and extract apiUrl, then send &url Mediafire to native extractor
        val handled = handleApiUrlAndUseMediafire(iframeSrc, subtitleCallback, callback)
        Log.d("FilmesOn", "Handled via Mediafire extractor: $handled")
    }
    
    private suspend fun handleApiUrlAndUseMediafire(
        playerUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val headers = buildPlayerHeaders(playerUrl)
            Log.d("FilmesOn", "Requesting player page: $playerUrl")
            val html = app.get(playerUrl, headers = headers).text
            val apiUrl = extractApiUrl(html)
            Log.d("FilmesOn", "Extracted API URL: $apiUrl")
            if (apiUrl.isNullOrBlank()) return false
            val mediafireEncoded = Regex("[?&]url=([^&]+)").find(apiUrl)?.groupValues?.get(1)
            val mediafireUrl = mediafireEncoded?.let { URLDecoder.decode(it, "UTF-8") }
            Log.d("FilmesOn", "Decoded Mediafire URL: $mediafireUrl")
            if (mediafireUrl.isNullOrBlank()) return false
            val ok = loadExtractor(mediafireUrl, referer = "https://www.mediafire.com", subtitleCallback = subtitleCallback, callback = callback)
            ok
        } catch (e: Exception) {
            false
        }
    }
    
    private fun parseIframeUrl(iframeSrc: String): Pair<String, String> {
        val parts = iframeSrc.split("?")
        return Pair(parts[0], parts.getOrNull(1) ?: "")
    }
    
    private fun extractPlayerParams(playerParams: String): Pair<String?, String?> {
        val id = Regex("id=([^&]+)").find(playerParams)?.groupValues?.get(1)
        val sub = Regex("sub=([^&]+)").find(playerParams)?.groupValues?.get(1)
        return Pair(id, sub)
    }
    
    private suspend fun extractVideoUrl(requestUrl: String, refererUrl: String): String? {
        val headers = buildPlayerHeaders(refererUrl)
        
        return try {
            Log.d("FilmesOn", "Requesting player page: $requestUrl")
            val playerResponse = app.get(requestUrl, headers = headers).text
            val apiUrl = extractApiUrl(playerResponse)
            
            Log.d("FilmesOn", "Extracted API URL: $apiUrl")
            
            if (apiUrl != null) {
                val mp4Url = fetchMp4FromApi(apiUrl, refererUrl)
                Log.d("FilmesOn", "Fetched MP4 from API URL: $mp4Url")
                mp4Url
            } else null
        } catch (e: Exception) {
            Log.d("FilmesOn", "Failed to extract video URL: ${e.message}")
            null
        }
    }
    
    private suspend fun fetchMp4FromApi(apiUrl: String, refererUrl: String): String? {
        return try {
            val headers = buildApiHeaders()
            val body = app.get(apiUrl, headers = headers).text.trim()
            Log.d("FilmesOn", "API body: $body")
            val downloadMatch = Regex("https://download[^\\s\"']+\\.mp4").find(body)
            val candidate = when {
                downloadMatch != null -> downloadMatch.value
                body.startsWith("http") && body.contains(".mp4") -> body
                else -> null
            }
            Log.d("FilmesOn", "Parsed MP4 candidate: $candidate")
            candidate
        } catch (e: Exception) {
            Log.d("FilmesOn", "Failed to fetch MP4 from API: ${e.message}")
            null
        }
    }
    
    private fun buildPlayerHeaders(refererUrl: String): Map<String, String> {
        return mapOf(
            "cookie" to "SITE_TOTAL_ID=aNrnY4pRyAcwRucDxsIKTQAAAMQ",
            "referer" to refererUrl,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
            "cache-control" to "no-cache",
            "dnt" to "1",
            "pragma" to "no-cache",
            "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-fetch-dest" to "iframe",
            "sec-fetch-mode" to "navigate",
            "sec-fetch-site" to "same-origin",
            "sec-gpc" to "1",
            "upgrade-insecure-requests" to "1",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )
    }
    
    private fun extractApiUrl(playerResponse: String): String? {
        return Regex("const apiUrl = `([^`]+)`")
            .find(playerResponse)
            ?.groupValues
            ?.get(1)
    }
    
    private fun buildApiHeaders(): Map<String, String> {
        return mapOf(
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
            "cache-control" to "no-cache",
            "dnt" to "1",
            "pragma" to "no-cache",
            "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-fetch-dest" to "document",
            "sec-fetch-mode" to "navigate",
            "sec-fetch-site" to "none",
            "upgrade-insecure-requests" to "1",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
        )
    }
    
    private suspend fun createExtractorLink(mp4Url: String, videoType: String, embedData: String): ExtractorLink {
        return newExtractorLink(
            "FilmesOn",
            "FilmesOn - $videoType",
            mp4Url,
            INFER_TYPE
        ) {
            this.referer = embedData
        }
    }
    
    private fun createSubtitleFile(sub: String): SubtitleFile {
        return SubtitleFile(
            lang = "Português",
            url = sub
        )
    }
}

// MARK: - Data Classes
private data class ContentInfo(
    val title: String,
    val poster: String?,
    val background: String?,
    val genres: List<String>,
    val tags: List<String>,
    val year: Int?,
    val plot: String,
    val rating: String?,
    val duration: Int?,
    val actors: List<Actor>,
    val directors: List<String>
)