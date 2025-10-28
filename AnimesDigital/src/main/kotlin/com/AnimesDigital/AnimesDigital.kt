package com.AnimesDigital

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.DubStatus
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.util.Base64

class AnimesDigital : MainAPI() {

    // base code reference and fork: https://github.com/oliveira-clouds/TestPlugins/blob/master/AnimesDigital/src/main/kotlin/com/AnimesDigital/AnimesDigital.kt (thanks buddy)

    // MARK: Config
    override var mainUrl = "https://animesdigital.org"
    override var name = "AnimesDigital"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val supportedTypes = setOf(TvType.Anime)
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "$mainUrl/home" to "Animes - Últimos Episódios",
        "$mainUrl/animes-legendados-online" to "Animes - Legendados", 
        "$mainUrl/animes-dublado" to "Animes - Dublados",
        "$mainUrl/filmes" to "Animes - Filmes",
        "$mainUrl/desenhos-online" to "Desenhos Animados"
    )

    // MARK: Main Page
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return when {
            request.data.contains("home") -> getHomePage(request)
            else -> getAnimesFromAPI(page, request)
        }
    }

    private suspend fun getHomePage(request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home: List<SearchResponse> = document.select(".itemE, .itemA").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(request.name, home, isHorizontalImages = true),
            hasNext = false
        )
    }

    // MARK: API & Token
    private suspend fun getSecurityToken(url: String): String? {
        return try { app.get(url).document.selectFirst(".menu_filter_box")?.attr("data-secury") } catch (e: Exception) { null }
    }

    private suspend fun getAnimesFromAPI(page: Int, request: MainPageRequest): HomePageResponse {
        val (typeUrl, filterAudio) = when {
            request.data.contains("animes-dublado") -> "animes" to "dublado"
            request.data.contains("animes-legendados") -> "animes" to "legendado"
            request.data.contains("filmes") -> "filmes" to "0"
            request.data.contains("desenhos") -> "desenhos" to "0"
            else -> "animes" to "animes"
        }

        val token = getSecurityToken(request.data) ?: "c1deb78cd4"
        val refUrl = buildReferrerUrl(request.data, page, typeUrl, filterAudio)
        val postData = buildPostData(page, typeUrl, filterAudio, token)

        return try {
            val response = app.post("$mainUrl/func/listanime", headers = apiHeaders(refUrl), data = postData)
            val home = parseApiResponse(response.text)
            val hasNext = page < extractTotalPage(response.text)
            newHomePageResponse(list = HomePageList(request.name, home), hasNext = hasNext)
        } catch (e: Exception) { getFallbackPage(request) }
    }

    private fun buildReferrerUrl(base: String, page: Int, typeUrl: String, filterAudio: String): String {
        val query = listOf(
            "filter_letter=0",
            "type_url=$typeUrl",
            "filter_audio=$filterAudio",
            "filter_order=name",
            "filter_genre_add=",
            "filter_genre_del=",
            "pagina=$page",
            "search=0",
            "limit=30"
        ).joinToString("&")
        return if (base.contains("?")) "$base&$query" else "$base?$query"
    }

    private fun buildPostData(page: Int, typeUrl: String, filterAudio: String, token: String) = mapOf(
        "token" to token,
        "pagina" to page.toString(),
        "search" to "0",
        "limit" to "30",
        "type" to "lista",
        "filters" to """{"filter_data":"filter_letter=0&type_url=$typeUrl&filter_audio=$filterAudio&filter_order=name","filter_genre_add":[],"filter_genre_del":[]}"""
    )

    private fun apiHeaders(ref: String) = mapOf(
        "accept" to "application/json, text/javascript, */*; q=0.01",
        "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "x-requested-with" to "XMLHttpRequest",
        "referer" to ref
    )

    private fun parseApiResponse(json: String): List<SearchResponse> {
        val list = mutableListOf<SearchResponse>()
        try {
            val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val html = arr.optString(i).replace("\\\"", "\"").replace("\\/", "/")
                org.jsoup.Jsoup.parseBodyFragment(html).selectFirst(".itemA")?.toSearchResultAlternative()?.let { sr ->
    list.add(sr)
}
            }
        } catch (e: Exception) { }
        return list
    }

    private fun extractTotalPage(json: String): Int = try { JSONObject(json).optInt("total_page", 1) } catch (e: Exception) { 1 }

    // MARK: Fallback Page
    private suspend fun getFallbackPage(request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select(".itemA, .anime-item, .item, .post, [class*='anime']").mapNotNull { it.toSearchResultAlternative() }
        val hasNext = document.select(".pagination a.current + a, .pagination a:contains(Próximo)").isNotEmpty()
        return newHomePageResponse(HomePageList(request.name, home, isHorizontalImages = false), hasNext)
    }

    // MARK: Search
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.itemE, div.itemA").mapNotNull { it.toSearchResultAlternative() }
    }

    // MARK: Load
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        return if (url.contains("/video/a/")) loadEpisode(url, document) else loadAnime(url, document)
    }

    private fun String?.toStatus() = this?.lowercase()?.let {
        when {
            it.contains("completo") -> 2
            it.contains("em lançamento") -> 1
            else -> null
        }
    }

    // MARK: Load Episode
    private suspend fun loadEpisode(url: String, doc: org.jsoup.nodes.Document): LoadResponse? {
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val animeTitle = doc.selectFirst(".info span:contains(Anime) + span")?.text()
            ?: doc.selectFirst("#anime_title")?.text()?.replace(" Episódio \\d+".toRegex(), "")?.trim()
            ?: title

        val episodeNum = extractCurrentEpisodeNumber(url, title)
        val currentEpisode = newEpisode("$url|#|$episodeNum") { name = "Episódio $episodeNum"; episode = episodeNum }
        val animeUrl = extractAnimeMainPageUrl(doc, url)

        return newAnimeLoadResponse(animeTitle, url, TvType.Anime) {
            posterUrl = poster
            plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            addEpisodes(DubStatus.Subbed, listOf(currentEpisode))
            if (animeUrl != null) recommendations = listOf(newAnimeSearchResponse("Ver todos os episódios", fixUrl(animeUrl), TvType.Anime) { posterUrl = poster })
        }
    }

    private fun extractAnimeMainPageUrl(doc: org.jsoup.nodes.Document, url: String): String? {
        return doc.selectFirst(".epsL a[href]")?.attr("href")
            ?: doc.selectFirst("a[href*='/anime/a/']")?.attr("href")
            ?: extractAnimeSlugFromUrl(url)?.let { "$mainUrl/anime/a/$it" }
    }

    private fun extractAnimeSlugFromUrl(url: String) = Regex("""/video/a/([^/]+)/""").find(url)?.groupValues?.get(1)

    private fun extractCurrentEpisodeNumber(url: String, title: String): Int {
        return Regex("""Epis[oó]dio\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""\b(\d+)\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
    }

    // MARK: Load Anime
private suspend fun loadAnime(url: String, doc: org.jsoup.nodes.Document): LoadResponse? {
    val info = doc.selectFirst(".single_anime, .single-content, .dados") ?: doc
    val title = info.selectFirst(".dados h1, h1.single-title, h1")?.text()?.trim()
        ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - Animes Online")?.trim()
        ?: doc.selectFirst("h1, h2")?.text() ?: return null

    val poster = fixUrlNull(
        info.selectFirst(".foto img")?.attr("src")
            ?: doc.selectFirst("img[src*=/uploads/]")?.attr("src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
    )
    val description = info.selectFirst(".dados .sinopse, .sinopse p")?.text()?.trim()
        ?: doc.selectFirst("meta[property=og:description]")?.attr("content")

    val tagsList = info.select(".dados .genres a, .generos a, .single-meta a[href*='genero']").map { it.text().trim() }
    val year = info.selectFirst(".dados .info:contains(Ano)")?.text()?.replace("Ano", "")?.trim()?.toIntOrNull()
    val tvType = if (url.contains("/filme/", ignoreCase = true)) TvType.Movie else TvType.Anime
    val dubStatus = if (url.contains("dublado", ignoreCase = true) || url.contains("desenhos", ignoreCase = true)) DubStatus.Dubbed else DubStatus.Subbed

    if (tvType == TvType.Movie) return newMovieLoadResponse(title, url, TvType.Movie, url) {
        posterUrl = poster
        plot = description
        this.tags = tagsList.toMutableList()
        this.year = year
    }

    val episodes = loadAllEpisodes(url, doc)
    return newAnimeLoadResponse(title, url, tvType) {
        posterUrl = poster
        plot = description
        this.tags = tagsList.toMutableList()
        this.year = year
        if (episodes.isNotEmpty()) addEpisodes(dubStatus, episodes)
    }
}

    // MARK: Episodes
    private suspend fun loadAllEpisodes(url: String, doc: org.jsoup.nodes.Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        var page = 1
        var hasNext = true
        var currentDoc = doc

        while (hasNext && page <= 20) {
            val pageEpisodes = extractEpisodesFromPage(currentDoc)
            val newEpisodes = pageEpisodes.filter { ep -> episodes.none { it.episode == ep.episode } }
            if (newEpisodes.isNotEmpty()) episodes.addAll(newEpisodes) else hasNext = false
            page++
            if (hasNext) currentDoc = app.get(buildNextPageUrl(url, page)).document
        }

        return episodes.sortedByDescending { it.episode }
    }

    private fun buildNextPageUrl(base: String, page: Int): String = if (base.contains("/page/")) base.replace(Regex("/page/\\d+/"), "/page/$page/") else "${base.removeSuffix("/")}/page/$page/"

    private fun extractEpisodesFromPage(doc: org.jsoup.nodes.Document): List<Episode> {
        return doc.select(".item_ep a").mapNotNull {
            val href = it.attr("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            var title = it.selectFirst("div.title_anime")?.text()?.trim()
                ?: it.selectFirst("img")?.attr("title")?.replace("Assistir ", "") ?: "Título Desconhecido"
            title = title.replace("Episodio ", "Episódio")
            val num = extractEpisodeNumber(title, href)
            newEpisode("$href|#|$num") { name = title; episode = num }
        }
    }

    private fun extractEpisodeNumber(title: String, url: String): Int {
        val patterns = listOf("""Epis[oó]dio\s*(\d+)""", """Cap\.?\s*(\d+)""", """\b(\d+)\b""").map { Regex(it, RegexOption.IGNORE_CASE) }
        patterns.forEach { it.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let { return it } }
        return Regex("""[\/\-](\d+)[\/\-]?""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    // MARK: Search Result
    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("a") ?: return null
        val href = titleEl.attr("href")
        val animeTitle = selectFirst(".title_anime")?.text()?.trim() ?: return null
        val episodeText = selectFirst(".number")?.text()?.trim() ?: return null
        val poster = selectFirst("img")?.attr("src")
        val episodeNum = episodeText.filter(Char::isDigit).toIntOrNull() ?: 1
        val isDub = animeTitle.contains("dublado", true) || href.contains("dublado", true) || episodeText.contains("dublado", true)
        return newAnimeSearchResponse("$animeTitle - $episodeText", href, TvType.Anime) { posterUrl = fixUrlNull(poster); addDubStatus(isDub, episodeNum) }
    }

    private fun Element.toSearchResultAlternative(): SearchResponse? {
        val titleEl = selectFirst("a") ?: selectFirst(".title, .name, h1, h2, h3") ?: return null
        val href = titleEl.attr("href")
        val title = titleEl.text().trim()
        val poster = selectFirst("img")?.attr("src") ?: selectFirst("img")?.attr("data-src")
        return if (title.isNotEmpty() && href.isNotEmpty()) {
            if (href.contains("/filme/", true) || title.contains("filme", true)) newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = fixUrlNull(poster) }
            else newAnimeSearchResponse(title, href, TvType.Anime) { posterUrl = fixUrlNull(poster) }
        } else null
    }

    // MARK: Load Links
    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var found = false
    val parts = data.split("|#|")
    val realUrl = parts[0]
    val epNum = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val doc = app.get(realUrl).document
    val isMovie = realUrl.contains("/filme/", true)

    val fhdLinks = mutableListOf<ExtractorLink>()
    val otherLinks = mutableListOf<ExtractorLink>()

    fun addLink(link: ExtractorLink, isFHD: Boolean) {
        if (isFHD) fhdLinks.add(link) else otherLinks.add(link)
    }

    if (isMovie) {
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src") ?: return@forEach
            when {
                src.contains("anivideo.net") && src.contains("m3u8") -> {
                    extractM3u8Url(src)?.let {
                        addLink(newExtractorLink(name, "Player FHD", it, ExtractorLinkType.M3U8) {
                            referer = realUrl
                            quality = Qualities.Unknown.value
                        }, isFHD = true)
                        found = true
                    }
                }
                else -> {
                    loadExtractor(src, realUrl, subtitleCallback) { ex -> addLink(ex, isFHD = false) }
                    found = true
                }
            }
        }
    } else {
        doc.select(".tab-video iframe[src]").forEach { iframe ->
            val src = iframe.attr("src") ?: return@forEach
            when {
                src.contains("anivideo.net") && src.contains("m3u8") -> {
                    extractM3u8Url(src)?.let {
                        addLink(newExtractorLink(name, "Player FHD", it, ExtractorLinkType.M3U8) {
                            referer = realUrl
                            quality = Qualities.Unknown.value
                        }, isFHD = true)
                        found = true
                    }
                }
                src.contains("animesdigital.org/aHR0") -> decodeAnimesDigitalUrl(src)?.let { url ->
                    val playerPage = app.get(url).document
                    playerPage.select(".post-body iframe[src]").getOrNull(epNum - 1)?.attr("src")?.takeIf(String::isNotBlank)?.let {
                        loadExtractor(it, url, subtitleCallback) { ex -> addLink(ex, isFHD = false) }
                        found = true
                    }
                }
                else -> {
                    loadExtractor(src, realUrl, subtitleCallback) { ex -> addLink(ex, isFHD = false) }
                    found = true
                }
            }
        }
    }

    (fhdLinks + otherLinks).forEach { callback(it) }
    return found
}

    private fun extractM3u8Url(src: String): String? = try { src.split("?").last().split("&").find { it.startsWith("d=") }?.substringAfter("=")?.let { java.net.URLDecoder.decode(it, "UTF-8") } } catch (e: Exception) { null }
    private fun decodeAnimesDigitalUrl(src: String): String? = try { Base64.getDecoder().decode(src.substringAfter("animesdigital.org/").substringBefore("/")).let { String(it) } } catch (e: Exception) { null }
}