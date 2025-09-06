package com.StarckFilmes

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class StarckFilmes : MainAPI() {
    override var mainUrl = "https://www.starckfilmes.online"
    override var name = "StarckFilmes"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)

    override val mainPage = mainPageOf(
        "?genre=ação" to "Ação",
        "?genre=animação" to "Animação",
        "?genre=aventura" to "Aventura",
        "?genre=comédia" to "Comédia",
        "?genre=crime" to "Crime",
        "?genre=documentário" to "Documentários",
        "?genre=drama" to "Drama",
        "?genre=família" to "Família",
        "?genre=ficção" to "Ficção",
        "?genre=ficção-científica" to "Ficção-Científica",
        "?genre=guerra" to "Guerra",
        "?genre=mistério" to "Mistério",
        "?genre=romance" to "Romance",
        "?genre=suspense" to "Suspense",
        "?genre=terror" to "Terror"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document
        val home = document.select("div.home.post-catalog div.item").mapNotNull { it.toSearchResult() }
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
        val title = this.select("a.title").text().trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.select("div.post-image-sub").attr("data-bk")
        
        val isSeries = title.contains("S0", ignoreCase = true) || 
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true)
        
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

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.home.post-catalog div.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("div.post-description h2.post-title")?.text()?.trim() ?: ""
        
        var poster = document.selectFirst("div.post-image img")?.attr("src")
        
        if (poster.isNullOrBlank()) {
            val trailerDiv = document.selectFirst("div.trailer")
            val youtubeLink = trailerDiv?.attr("data-youtube-link")
            
            if (!youtubeLink.isNullOrBlank()) {
                val youtubeIdMatch = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]+)""").find(youtubeLink)
                if (youtubeIdMatch != null) {
                    val videoId = youtubeIdMatch.groupValues[1]
                    poster = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                }
            }
        }
        
        val description = document.selectFirst("div.sinopse span:last-child")?.text()?.trim()
        
        val yearElement = document.select("div.post-description p").find { element ->
            element.text().contains("Lançamento:", ignoreCase = true)
        }
        val yearText = yearElement?.select("span:last-child")?.text()?.trim()
        val year = yearText?.toIntOrNull()
        
        val genreElement = document.select("div.post-description p").find { element ->
            element.text().contains("Gênero:", ignoreCase = true)
        }
        
        val genresText = genreElement?.select("span:last-child")?.text()?.trim()
        val genres = genresText?.split(", ")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        
        val quality = document.selectFirst("div.post-description div.meta span.sl-quality")?.text()?.trim()
        if (!quality.isNullOrBlank()) {
            genres.add(0, "Qualidade: $quality")
        }
        
        val durationElement = document.select("div.post-description div.meta span").find { element ->
            element.text().contains("min", ignoreCase = true)
        }
        val duration = durationElement?.text()?.trim()
        if (!duration.isNullOrBlank()) {
            genres.add(0, "Duração: $duration")
        }
        
        val isSeries = document.select("div.post-buttons div.epsodios").isNotEmpty() ||
                      title.contains("S0", ignoreCase = true) ||
                      title.contains("Temporada", ignoreCase = true) ||
                      title.contains("Season", ignoreCase = true)
        
        return if (isSeries) {
            val episodes = loadEpisodesFromPage(document, url)
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
                this.tags = genres
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val isEpisode = data.contains("|")
        val url: String
        val episodeNumber: Int?
        
        if (isEpisode) {
            val parts = data.split("|")
            url = parts[0]
            episodeNumber = parts[1].toIntOrNull()
        } else {
            url = data
            episodeNumber = null
        }
        
        val document = app.get(url).document
        
        return if (isEpisode && episodeNumber != null) {
            val episodeResult = loadEpisodeLinks(document, episodeNumber, subtitleCallback, callback)
            if (!episodeResult) {
                val singleSeasonMagnet = document.select("div.post-buttons div.buttons-content a[href^='magnet:']")
                val episodiosDiv = document.select("div.post-buttons div.epsodios")
                
                if (singleSeasonMagnet.isNotEmpty() && episodiosDiv.isEmpty()) {
                    return false
                }
            }
            episodeResult
        } else {
            loadMovieLinks(document, subtitleCallback, callback)
        }
    }
    
    private suspend fun loadEpisodeLinks(
        document: org.jsoup.nodes.Document,
        episodeNumber: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val episodiosDiv = document.select("div.post-buttons div.epsodios")
        if (episodiosDiv.isEmpty()) {
            return false
        }
        
        val allParagraphs = episodiosDiv.select("p")
        
        val episodeParagraph = allParagraphs.find { paragraph ->
            val text = paragraph.text().trim()
            
            val episodeMatch = Regex("EPISÓDIOS\\s*0?$episodeNumber:", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPISÓDIO\\s*0?$episodeNumber:", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPIS.*DIOS\\s*0?$episodeNumber", RegexOption.IGNORE_CASE).find(text) ?:
                             Regex("EPIS.*DIO\\s*0?$episodeNumber", RegexOption.IGNORE_CASE).find(text)
            
            episodeMatch != null
        }
        
        if (episodeParagraph == null) {
            return false
        }
        
        val magnetLinks = episodeParagraph.select("a[href^='magnet:']")
        
        if (magnetLinks.isEmpty()) {
            return false
        }

        for (link in magnetLinks) {
            val originalMagnetUrl = link.attr("href")
            val enhancedMagnetUrl = enhanceMagnetWithTrackers(originalMagnetUrl)
            val quality = link.text().trim()
            
            val magnetInfo = extractMagnetInfo(enhancedMagnetUrl)
            
            val sourceName = buildString {
                append("StarckFilmes Ep$episodeNumber")
                append(" $quality")
                if (magnetInfo.contains("DUAL", ignoreCase = true)) append(" Dual Áudio")
                if (magnetInfo.contains("MKV", ignoreCase = true)) append(" MKV")
                if (magnetInfo.contains("MP4", ignoreCase = true)) append(" MP4")
            }
            
            callback(
                newExtractorLink(
                    sourceName,
                    sourceName,
                    enhancedMagnetUrl,
                    ExtractorLinkType.MAGNET
                ) {
                    this.referer = mainUrl
                }
            )
        }
        
        val subtitleLink = document.selectFirst("a[href*='opensubtitles.org']")
        if (subtitleLink != null) {
            val subtitleUrl = subtitleLink.attr("href")
            
            try {
                subtitleCallback(SubtitleFile("pt", subtitleUrl))
            } catch (e: Exception) {
            }
        }
        
        return true
    }
    
    private suspend fun loadMovieLinks(
        document: org.jsoup.nodes.Document,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val magnetLinks = document.select("a[href^='magnet:']")
        
        if (magnetLinks.isEmpty()) {
            return false
        }

        for (link in magnetLinks) {
            val originalMagnetUrl = link.attr("href")
            val enhancedMagnetUrl = enhanceMagnetWithTrackers(originalMagnetUrl)
            
            val buttonContainer = link.closest("span.btn-down")
            val textSpans = buttonContainer?.select("span.text span")
            
            var audioType = ""
            var format = ""
            var quality = ""
            var size = ""
            
            textSpans?.forEach { span ->
                val text = span.text().trim()
                
                when {
                    text.contains("Dual Áudio", ignoreCase = true) -> audioType = "Dual Áudio"
                    text.contains("Legendado", ignoreCase = true) -> audioType = "Legendado"
                    text.contains("Dublado", ignoreCase = true) -> audioType = "Dublado"
                    text.contains("MKV", ignoreCase = true) -> format = "MKV"
                    text.contains("MP4", ignoreCase = true) -> format = "MP4"
                    text.contains("AVI", ignoreCase = true) -> format = "AVI"
                    text.contains("1080p", ignoreCase = true) -> quality = "1080p"
                    text.contains("720p", ignoreCase = true) -> quality = "720p"
                    text.contains("480p", ignoreCase = true) -> quality = "480p"
                    text.matches(Regex(".*\\(.*GB.*\\).*")) -> size = text
                }
            }
            
            val sourceName = buildString {
                append("StarckFilmes")
                if (audioType.isNotEmpty()) append(" $audioType")
                if (quality.isNotEmpty()) append(" $quality")
                if (format.isNotEmpty()) append(" $format")
                if (size.isNotEmpty()) append(" ($size)")
            }
            
            val magnetInfo = extractMagnetInfo(enhancedMagnetUrl)
            
            val linkType = ExtractorLinkType.MAGNET
            
            callback(
                newExtractorLink(
                    sourceName,
                    sourceName,
                    enhancedMagnetUrl,
                    linkType
                ) {
                    this.referer = mainUrl
                }
            )
        }
        
        val subtitleLink = document.selectFirst("a[href*='opensubtitles.org']")
        if (subtitleLink != null) {
            val subtitleUrl = subtitleLink.attr("href")
            
            try {
                subtitleCallback(SubtitleFile("pt", subtitleUrl))
            } catch (e: Exception) {
            }
        }
        
        return true
    }
    
    private fun loadEpisodesFromPage(document: org.jsoup.nodes.Document, baseUrl: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        val episodiosDiv = document.select("div.post-buttons div.epsodios")
        if (episodiosDiv.isEmpty()) {
            return episodes
        }
        
        val episodeParagraphs = episodiosDiv.select("p")
        
        episodeParagraphs.forEach { paragraph ->
            val paragraphText = paragraph.text().trim()
            
            val episodeMatches = mutableListOf<Int>()
            
            val multipleMatch = Regex("EPISÓDIOS\\s*(\\d+)\\s*E\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
            if (multipleMatch != null) {
                val ep1 = multipleMatch.groupValues[1].toIntOrNull()
                val ep2 = multipleMatch.groupValues[2].toIntOrNull()
                if (ep1 != null) episodeMatches.add(ep1)
                if (ep2 != null) episodeMatches.add(ep2)
            } else {
                val singleMatch = Regex("EPISÓDIOS\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
                if (singleMatch != null) {
                    val episodeNumber = singleMatch.groupValues[1].toIntOrNull()
                    if (episodeNumber != null) episodeMatches.add(episodeNumber)
                } else {
                    val altMatch = Regex("EPISÓDIO\\s*(\\d+):", RegexOption.IGNORE_CASE).find(paragraphText)
                    if (altMatch != null) {
                        val episodeNumber = altMatch.groupValues[1].toIntOrNull()
                        if (episodeNumber != null) episodeMatches.add(episodeNumber)
                    }
                }
            }
            
            episodeMatches.forEach { episodeNumber ->
                val episodeMagnetLinks = paragraph.select("a[href^='magnet:']")
                
                val episode = newEpisode("$baseUrl|$episodeNumber") {
                    this.name = "Episódio $episodeNumber"
                    this.episode = episodeNumber
                    this.season = 1
                }
                
                episodes.add(episode)
            }
        }
        
        return episodes
    }
    
    private fun enhanceMagnetWithTrackers(magnetUrl: String): String {
        val premiumTrackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://retracker.hotplug.ru:2710/announce",
            "http://tracker.bt4g.com:2095/announce",
            "http://bt.okmp3.ru:2710/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "http://tracker.mywaifu.best:6969/announce",
            "udp://tracker.qu.ax:6969/announce",
            "http://tracker.privateseedbox.xyz:2710/announce",
            "udp://evan.im:6969/announce",
            "https://tracker.yemekyedim.com:443/announce",
            "udp://retracker.lanta.me:2710/announce",
            "udp://martin-gebhardt.eu:25/announce",
            "http://tracker.beeimg.com:6969/announce",
            "udp://tracker.yume-hatsuyuki.moe:6969/announce",
            "udp://udp.tracker.projectk.org:23333/announce",
            "http://tracker.renfei.net:8080/announce",
            "https://tracker.expli.top:443/announce",
            "https://tr.nyacat.pw:443/announce",
            "udp://extracker.dahrkael.net:6969/announce",
            "udp://tracker.hifitechindia.com:6969/announce",
            "http://ipv4.rer.lol:2710/announce",
            "udp://tracker.plx.im:6969/announce",
            "udp://tracker.skillindia.site:6969/announce",
            "udp://tracker.tvunderground.org.ru:3218/announce",
            "https://t.213891.xyz:443/announce",
            "udp://p4p.arenabg.com:1337/announce",
            "udp://tracker.dler.com:6969/announce",
            "https://tracker.moeblog.cn:443/announce",
            "udp://d40969.acod.regrucolo.ru:6969/announce",
            "https://tracker.jdx3.org:443/announce",
            "http://ipv6.rer.lol:6969/announce",
            "http://tracker.netmap.top:6969/announce",
            "udp://tracker.bitcoinindia.space:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://ttk2.nbaonlineservice.com:6969/announce",
            "https://pybittrack.retiolus.net:443/announce",
            "udp://bandito.byterunner.io:6969/announce",
            "udp://tracker.gigantino.net:6969/announce",
            "udp://tracker.rescuecrew7.com:1337/announce",
            "udp://tracker.torrust-demo.com:6969/announce",
            "udp://retracker01-msk-virt.corbina.net:80/announce",
            "udp://1c.premierzal.ru:6969/announce",
            "http://taciturn-shadow.spb.ru:6969/announce",
            "udp://tracker.kmzs123.cn:17272/announce",
            "udp://tracker.srv00.com:6969/announce",
            "https://tracker.aburaya.live:443/announce",
            "udp://tracker-udp.gbitt.info:80/announce",
            "udp://tracker.hifimarket.in:2710/announce",
            "udp://tracker.fnix.net:6969/announce",
            "udp://tracker.therarbg.to:6969/announce",
            "udp://www.torrent.eu.org:451/announce",
            "http://torrent.hificode.in:6969/announce",
            "https://tracker.ghostchu-services.top:443/announce",
            "udp://open.dstud.io:6969/announce",
            "http://tracker.ipv6tracker.ru:80/announce",
            "http://open.trackerlist.xyz:80/announce",
            "http://shubt.net:2710/announce",
            "http://0123456789nonexistent.com:80/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.valete.tf:9999/announce",
            "udp://tracker.gmi.gd:6969/announce",
            "https://tracker.zhuqiy.top:443/announce",
            "https://tracker.leechshield.link:443/announce",
            "wss://tracker.webtorrent.dev:443",
            "wss://tracker.files.fm:7073/announce",
            "wss://tracker.btorrent.xyz:443",
            "ws://tracker.files.fm:7072/announce",
            "udp://tracker.openbittorrent.com:80",
            "udp://tracker.leechers-paradise.org:6969",
            "udp://tracker.coppersurfer.tk:6969",
            "udp://glotorrents.pw:6969",
            "https://opentracker.8880085.xyz:443/announce",
            "udp://public.popcorn-tracker.org:6969/announce",

            "http://atrack.pow7.com/announce",
            "http://bt.henbt.com:2710/announce",
            "http://bt.pusacg.org:8080/announce",
            "http://bt2.careland.com.cn:6969/announce",
            "http://explodie.org:6969/announce",
            "http://mgtracker.org:2710/announce",
            "http://mgtracker.org:6969/announce",
            "http://open.acgtracker.com:1096/announce",
            "http://open.lolicon.eu:7777/announce",
            "http://open.touki.ru/announce.php",
            "http://p4p.arenabg.ch:1337/announce",
            "http://pow7.com:80/announce",
            "http://retracker.gorcomnet.ru/announce",
            "http://retracker.krs-ix.ru/announce",
            "http://retracker.krs-ix.ru:80/announce",
            "http://secure.pow7.com/announce",
            "http://t1.pow7.com/announce",
            "http://t2.pow7.com/announce",
            "http://thetracker.org:80/announce",
            "http://torrent.gresille.org/announce",
            "http://torrentsmd.com:8080/announce",
            "http://tracker.aletorrenty.pl:2710/announce",
            "http://tracker.baravik.org:6970/announce",
            "http://tracker.bittor.pw:1337/announce",
            "http://tracker.bittorrent.am/announce",
            "http://tracker.calculate.ru:6969/announce",
            "http://tracker.dler.org:6969/announce",
            "http://tracker.dutchtracking.com/announce",
            "http://tracker.dutchtracking.com:80/announce",
            "http://tracker.dutchtracking.nl/announce",
            "http://tracker.dutchtracking.nl:80/announce",
            "http://tracker.edoardocolombo.eu:6969/announce",
            "http://tracker.ex.ua/announce",
            "http://tracker.ex.ua:80/announce",
            "http://tracker.filetracker.pl:8089/announce",
            "http://tracker.flashtorrents.org:6969/announce",
            "http://tracker.grepler.com:6969/announce",
            "http://tracker.internetwarriors.net:1337/announce",
            "http://tracker.kicks-ass.net/announce",
            "http://tracker.kicks-ass.net:80/announce",
            "http://tracker.kuroy.me:5944/announce",
            "http://tracker.mg64.net:6881/announce",
            "http://tracker.skyts.net:6969/announce",
            "http://tracker.tfile.me/announce",
            "http://tracker.tiny-vps.com:6969/announce",
            "http://tracker.yoshi210.com:6969/announce",
            "http://tracker1.wasabii.com.tw:6969/announce",
            "http://tracker2.itzmx.com:6961/announce",
            "http://tracker2.wasabii.com.tw:6969/announce",
            "http://www.wareztorrent.com/announce",
            "http://www.wareztorrent.com:80/announce",

            "udp://9.rarbg.com:2710/announce",
            "udp://9.rarbg.me:2780/announce",
            "udp://9.rarbg.to:2730/announce",
            "udp://91.218.230.81:6969/announce",
            "udp://94.23.183.33:6969/announce",
            "udp://bt.xxx-tracker.com:2710/announce",
            "udp://eddie4.nl:6969/announce",
            "udp://shadowshq.eddie4.nl:6969/announce",
            "udp://shadowshq.yi.org:6969/announce",
            "udp://torrent.gresille.org:80/announce",
            "udp://tracker.aletorrenty.pl:2710/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.eddie4.nl:6969/announce",
            "udp://tracker.ex.ua:80/announce",
            "udp://tracker.filetracker.pl:8089/announce",
            "udp://tracker.flashtorrents.org:6969/announce",
            "udp://tracker.grepler.com:6969/announce",
            "udp://tracker.ilibr.org:80/announce",
            "udp://tracker.internetwarriors.net:1337/announce",
            "udp://tracker.kicks-ass.net:80/announce",
            "udp://tracker.kuroy.me:5944/announce",
            "udp://tracker.leechers-paradise.org:6969/announce",
            "udp://tracker.mg64.net:2710/announce",
            "udp://tracker.mg64.net:6969/announce",
            "udp://tracker.piratepublic.com:1337/announce",
            "udp://tracker.sktorrent.net:6969/announce",
            "udp://tracker.skyts.net:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.yoshi210.com:6969/announce",
            "udp://tracker2.indowebster.com:6969/announce",
            "udp://tracker4.piratux.com:6969/announce",
            "udp://zer0day.ch:1337/announce",
            "udp://zer0day.to:1337/announce",
            "https://tracker.gcrenwp.top:443/announce",
            "https://tracker.qingwa.pro:443/announce",
            "https://tracker.cutie.dating:443/announce",
            "http://tracker.tritan.gg:8080/announce",

            "https://tracker.pmman.tech:443/announce",
            "https://tracker.bt4g.com:443/announce",
            "https://tr.zukizuki.org:443/announce",
            "http://tracker.opentrackr.org:1337/announce",
            "http://open.tracker.cl:1337/announce",
            "http://www.torrentsnipe.info:2701/announce",
            "http://www.genesis-sp.org:2710/announce",
            
            "udp://tracker.ducks.party:1984/announce",
            "http://lucke.fenesisu.moe:6969/announce",
            "udp://tracker-de-2.cutie.dating:1337/announce",
            "udp://6ahddutb1ucc3cp.ru:6969/announce",
            "udp://tracker.cloudbase.store:1333/announce",
            "udp://tracker.zupix.online:1333/announce",
            "udp://opentracker.io:6969/announce",
            "udp://tracker.startwork.cv:1337/announce",
            "https://2.tracker.eu.org:443/announce",
            "https://4.tracker.eu.org:443/announce",
            "https://3.tracker.eu.org:443/announce",
            "udp://tr4ck3r.duckdns.org:6969/announce",
            "https://1.tracker.eu.org:443/announce",
            "https://torrent.tracker.durukanbal.com:443/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://wepzone.net:6969/announce",
            "udp://tracker2.dler.org:80/announce",
            "udp://tracker.zupix.online:6969/announce",
            "udp://tracker.wepzone.net:6969/announce",
            "udp://tracker.ololosh.space:6969/announce",
            "https://tracker.alaskantf.com:443/announce",
            
            "http://104.28.1.30:8080/announce",
            "http://104.28.16.69/announce",
            "http://107.150.14.110:6969/announce",
            "http://109.121.134.121:1337/announce",
            "http://114.55.113.60:6969/announce",
            "http://125.227.35.196:6969/announce",
            "http://128.199.70.66:5944/announce",
            "http://157.7.202.64:8080/announce",
            "http://158.69.146.212:7777/announce",
            "http://173.254.204.71:1096/announce",
            "http://178.175.143.27/announce",
            "http://178.33.73.26:2710/announce",
            "http://182.176.139.129:6969/announce",
            "http://185.5.97.139:8089/announce",
            "http://188.165.253.109:1337/announce",
            "http://194.106.216.222/announce",
            "http://195.123.209.37:1337/announce",
            "http://210.244.71.25:6969/announce",
            "http://210.244.71.26:6969/announce",
            "http://213.159.215.198:6970/announce",
            "http://213.163.67.56:1337/announce",
            "http://37.19.5.139:6969/announce",
            "http://37.19.5.155:6881/announce",
            "http://46.4.109.148:6969/announce",
            "http://5.79.249.77:6969/announce",
            "http://5.79.83.193:2710/announce",
            "http://51.254.244.161:6969/announce",
            "http://59.36.96.77:6969/announce",
            "http://74.82.52.209:6969/announce",
            "http://80.246.243.18:6969/announce",
            "http://81.200.2.231/announce",
            "http://85.17.19.180/announce",
            "http://87.248.186.252:8080/announce",
            "http://87.253.152.137/announce",
            "http://91.216.110.47/announce",
            "http://91.217.91.21:3218/announce",
            "http://93.92.64.5/announce",
            "http://bt.henbt.com:2710/announce",
            "http://bt.pusacg.org:8080/announce",
            "http://bt2.careland.com.cn:6969/announce",
            "http://open.acgtracker.com:1096/announce",
            "http://open.lolicon.eu:7777/announce",
            "http://open.touki.ru/announce.php",
            "http://p4p.arenabg.ch:1337/announce",
            "http://p4p.arenabg.com:1337/announce",
            "http://retracker.gorcomnet.ru/announce",
            "http://retracker.krs-ix.ru/announce",
            "http://retracker.krs-ix.ru:80/announce",
            "http://torrent.gresille.org/announce",
            "http://torrentsmd.com:8080/announce",
            "http://tracker.baravik.org:6970/announce",
            "http://tracker.bittor.pw:1337/announce",
            "http://tracker.bittorrent.am/announce",
            "http://tracker.calculate.ru:6969/announce",
            "http://tracker.dler.org:6969/announce",
            "http://tracker.dutchtracking.com/announce",
            "http://tracker.dutchtracking.com:80/announce",
            "http://tracker.dutchtracking.nl/announce",
            "http://tracker.dutchtracking.nl:80/announce",
            "http://tracker.edoardocolombo.eu:6969/announce",
            "http://tracker.ex.ua/announce",
            "http://tracker.ex.ua:80/announce",
            "http://tracker.filetracker.pl:8089/announce",
            "http://tracker.flashtorrents.org:6969/announce",
            "http://tracker.grepler.com:6969/announce",
            "http://tracker.internetwarriors.net:1337/announce",
            "http://tracker.kicks-ass.net/announce",
            "http://tracker.kicks-ass.net:80/announce",
            "http://tracker.kuroy.me:5944/announce",
            "http://tracker.mg64.net:6881/announce",
            "http://tracker.skyts.net:6969/announce",
            "http://tracker.tfile.me/announce",
            "http://tracker.tiny-vps.com:6969/announce",
            "http://tracker.yoshi210.com:6969/announce",
            "http://tracker1.wasabii.com.tw:6969/announce",
            "http://tracker2.itzmx.com:6961/announce",
            "http://tracker2.wasabii.com.tw:6969/announce",
            "http://www.wareztorrent.com/announce",
            "http://www.wareztorrent.com:80/announce",
            "https://104.28.17.69/announce",
            "https://www.wareztorrent.com/announce",
            "udp://107.150.14.110:6969/announce",
            "udp://109.121.134.121:1337/announce",
            "udp://114.55.113.60:6969/announce",
            "udp://128.199.70.66:5944/announce",
            "udp://151.80.120.114:2710/announce",
            "udp://168.235.67.63:6969/announce",
            "udp://178.33.73.26:2710/announce",
            "udp://182.176.139.129:6969/announce",
            "udp://185.5.97.139:8089/announce",
            "udp://185.86.149.205:1337/announce",
            "udp://188.165.253.109:1337/announce",
            "udp://191.101.229.236:1337/announce",
            "udp://194.106.216.222:80/announce",
            "udp://195.123.209.37:1337/announce",
            "udp://195.123.209.40:80/announce",
            "udp://208.67.16.113:8000/announce",
            "udp://213.163.67.56:1337/announce",
            "udp://37.19.5.155:2710/announce",
            "udp://46.4.109.148:6969/announce",
            "udp://5.79.249.77:6969/announce",
            "udp://5.79.83.193:6969/announce",
            "udp://51.254.244.161:6969/announce",
            "udp://62.138.0.158:6969/announce",
            "udp://62.212.85.66:2710/announce",
            "udp://74.82.52.209:6969/announce",
            "udp://85.17.19.180:80/announce",
            "udp://89.234.156.205:80/announce",
            "udp://bt.xxx-tracker.com:2710/announce",
            "udp://eddie4.nl:6969/announce",
            "udp://mgtracker.org:2710/announce",
            "udp://shadowshq.eddie4.nl:6969/announce",
            "udp://shadowshq.yi.org:6969/announce",
            "udp://torrent.gresille.org:80/announce",
            "udp://tracker.aletorrenty.pl:2710/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.coppersurfer.tk:6969/announce",
            "udp://tracker.eddie4.nl:6969/announce",
            "udp://tracker.ex.ua:80/announce",
            "udp://tracker.filetracker.pl:8089/announce",
            "udp://tracker.flashtorrents.org:6969/announce",
            "udp://tracker.grepler.com:6969/announce",
            "udp://tracker.ilibr.org:80/announce",
            "udp://tracker.internetwarriors.net:1337/announce",
            "udp://tracker.kicks-ass.net:80/announce",
            "udp://tracker.kuroy.me:5944/announce",
            "udp://tracker.leechers-paradise.org:6969/announce",
            "udp://tracker.mg64.net:2710/announce",
            "udp://tracker.mg64.net:6969/announce",
            "udp://tracker.piratepublic.com:1337/announce",
            "udp://tracker.sktorrent.net:6969/announce",
            "udp://tracker.skyts.net:6969/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.yoshi210.com:6969/announce",
            "udp://tracker2.indowebster.com:6969/announce",
            "udp://tracker4.piratux.com:6969/announce",
            "udp://zer0day.ch:1337/announce",
            "udp://zer0day.to:1337/announce",
            "udp://tracker.theoks.net:6969/announce"


        )
        
        try {
            val decodedUrl = java.net.URLDecoder.decode(magnetUrl, "UTF-8")
            
            val existingTrackers = mutableSetOf<String>()
            val trackerMatches = Regex("&tr=([^&]+)").findAll(decodedUrl)
            
            trackerMatches.forEach { match ->
                val tracker = java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
                existingTrackers.add(tracker)
            }
            
            val newTrackers = premiumTrackers.filter { tracker ->
                val trackerDomain = tracker.substringAfter("://").substringBefore("/")
                !existingTrackers.any { existing ->
                    existing.contains(trackerDomain)
                }
            }
            
            if (newTrackers.isNotEmpty()) {
                val enhancedUrl = buildString {
                    append(decodedUrl)
                    newTrackers.forEach { tracker ->
                        append("&tr=")
                        append(java.net.URLEncoder.encode(tracker, "UTF-8"))
                    }
                }
                return enhancedUrl
            }
            
            return magnetUrl
        } catch (e: Exception) {
            return magnetUrl
        }
    }
    
    private fun extractMagnetInfo(magnetUrl: String): String {
        return try {
            val decodedUrl = java.net.URLDecoder.decode(magnetUrl, "UTF-8")
            
            val nameMatch = Regex("&dn=([^&]+)").find(decodedUrl)
            val fileName = nameMatch?.groupValues?.get(1) ?: ""
            
            val extensionMatch = Regex("\\.([a-zA-Z0-9]+)(?:[?&]|$)").find(fileName)
            val extension = extensionMatch?.groupValues?.get(1) ?: ""
            
            "$fileName ($extension)"
        } catch (e: Exception) {
            "desconhecido"
        }
    }
} 