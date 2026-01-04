package com.Doramas

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Doramas : MainAPI() {
    override var mainUrl = "https://doramasonline.co"
    override var name = "Doramas"
    override var lang = "pt-br"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/category/lancamentos/" to "Lançamentos",
        "$mainUrl/category/comedia/" to "Comédia",
        "$mainUrl/category/crime/" to "Crime",
        "$mainUrl/category/documentario/" to "Documentário",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/familia/" to "Família",
        "$mainUrl/category/misterio/" to "Mistério"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + if (page > 1) "page/$page/" else ""
        val home = app.get(url).document
            .select("div.aa-cn div#movies-a ul.post-lst li")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document
            .select("div.aa-cn div#movies-a ul.post-lst li")
            .mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("header.entry-header h2.entry-title")?.text() ?: return null
        val href = selectFirst("a.lnk-blk")?.attr("href") ?: return null
        val poster = selectPoster("div.post-thumbnail figure img", "/w500/")
        val year = selectFirst("span.year")?.text()?.toIntOrNull()
        val ratingText = selectFirst("div.entry-meta span.vote")?.text()
            ?.replace("TMDB", "")
            ?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = poster
            this.year = year
            this.quality = getQualityFromString(selectFirst("span.post-ql")?.text())
            this.score = Score.from10(ratingText)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("aside.fg1 header.entry-header h1.entry-title")?.text() ?: return null
        val poster = document.selectPoster("div.bghd img.TPostBg", "/w1280/")
        val year = document.extractInt("span.year")
        val durationText = document.extractText("span.duration")
        val score = document.selectFirst("div.vote-cn span.vote span.num")?.text()?.toDoubleOrNull()
        val plot = document.selectFirst("aside.fg1 div.description p")?.text()
        val genres = document.select("span.genres a").map { it.text() }
        val actors = document.selectActors()
        val trailer = document.selectFirst("div.mdl-cn iframe")?.attr("src")
        
        val iframeUrl = document.selectFirst("iframe[src*='seriesboa.live']")?.attr("src")
        val isSerie = url.contains("/serie/")

        return if (isSerie) {
            val episodes = if (iframeUrl != null) {
                parseEpisodes(app.get(iframeUrl).document)
            } else emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.score = Score.from10(score)
                if (!actors.isNullOrEmpty()) addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, iframeUrl ?: "") {
                posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.duration = parseDuration(durationText)
                this.score = Score.from10(score)
                if (!actors.isNullOrEmpty()) addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false


        val idOnly = Regex("""(\d+)$""").find(data.trimEnd('/'))?.value
        
        if (idOnly == null) {
            return false
        }

        val playerPageUrl = "https://seriesboa.live/episodio/$idOnly"

        val response = app.get(playerPageUrl)
        val playerDoc = response.document
        val links = extractEmbedLinks(playerDoc)
        

        links.forEach { link ->
            if (link.contains("playembedapi")) return@forEach

            when {
                link.contains("embedplay.upns") ->
                    EmbedPlayUpnsPro().getUrl(link, playerPageUrl, subtitleCallback, callback)

                link.contains("embedplay.upn.one") ->
                    EmbedPlayUpnOne().getUrl(link, playerPageUrl, subtitleCallback, callback)

                else ->
                    loadExtractor(link, playerPageUrl, subtitleCallback, callback)
            }
        }
        return links.isNotEmpty()
    }

    private fun parseEpisodes(doc: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seasons = doc.select("ul.header-navigation li[data-season-id]")

        seasons.forEach { season ->
            val seasonNumber = season.attr("data-season-number").toIntOrNull() ?: 1
            val seasonId = season.attr("data-season-id")

            doc.select("li[data-season-id='$seasonId']").forEach { ep ->
                val epId = ep.attr("data-episode-id")
                if (epId.isBlank()) return@forEach

                val name = ep.selectFirst("a")?.text().orEmpty()
                val number = Regex("\\d+").find(name)?.value?.toIntOrNull() ?: 1

                episodes += newEpisode(epId) {
                    this.name = name.trim()
                    this.season = seasonNumber
                    this.episode = number
                }
            }
        }
        return episodes.distinctBy { it.data }
    }

    private fun extractEmbedLinks(doc: Document): List<String> {
        val buttons = doc.select("button[data-source]").mapNotNull { it.attr("data-source") }
        val iframes = doc.select("div#player iframe, div.play-overlay iframe").mapNotNull { it.attr("src") }
        return (buttons + iframes).distinct().filter { it.isNotBlank() }
    }

    private fun Document.selectActors(): List<Pair<Actor, ActorRole?>>? {
        val actors = select("ul.cast-lst a").map {
            Actor(it.text(), it.attr("href")) to null
        }
        return actors.ifEmpty { null }
    }

    private fun Element.selectPoster(selector: String, replace: String): String? {
        val img = selectFirst(selector) ?: return null
        val src = img.attr("src").ifBlank { img.attr("data-src") }
        return src.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.replace(replace, "/original/")
    }

    private fun Document.selectPoster(selector: String, replace: String): String? = 
        (this as Element).selectPoster(selector, replace)

    private fun Document.extractInt(selector: String): Int? =
        selectFirst(selector)?.text()?.filter { it.isDigit() }?.toIntOrNull()

    private fun Document.extractText(selector: String): String? =
        selectFirst(selector)?.text()

    private fun parseDuration(text: String?): Int? {
        if (text == null) return null
        Regex("(\\d+)h\\s*(\\d+)m").find(text)?.let {
            return (it.groupValues[1].toIntOrNull() ?: 0) * 60 +
                    (it.groupValues[2].toIntOrNull() ?: 0)
        }
        return Regex("(\\d+)m").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }
}