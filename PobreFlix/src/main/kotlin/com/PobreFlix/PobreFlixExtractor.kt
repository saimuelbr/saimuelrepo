package com.PobreFlix

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

object PobreFlixExtractor {

    private const val BASE_PLAYER = "https://www.pobreflixtv.club/e/getplay.php"
    private const val TAG = "PobreFlix"

    private val videoRegex = Regex("""C_Video\('(\d+)','(.*?)'\)""")
    private val serverRegex = Regex("""'([^']*)'\)""")

    private val serverPriority = mapOf(
        "streamtape" to 1,
        "filemoon" to 2,
        "doodstream" to 3,
        "mixdrop" to 4
    )

    suspend fun getLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val (type, rawUrl) = parseData(data)
        val url = buildPlayerUrl(type, rawUrl)

        return runCatching {
            val document = app.get(url).document

            val items = document.select("div.item[onclick*='C_Video']")

            if (items.isEmpty()) {
            }

            items.sortedBy { extractPriority(it.attr("onclick")) }
                .forEach { item ->
                    val onClickAttr = item.attr("onclick")
                    processItem(onClickAttr, url, subtitleCallback, callback)
                }

            true
        }.getOrElse { 
            false 
        }
    }

    private fun parseData(data: String): Pair<String, String> {
        val parts = data.split("|", limit = 2)
        return Pair(parts.getOrNull(0).orEmpty(), parts.getOrNull(1) ?: data)
    }

    private fun buildPlayerUrl(type: String, url: String): String {
        if (type != "movie") return url
        return if (url.contains("?")) "$url&area=online" else "$url/?area=online"
    }

    private fun extractPriority(onClick: String): Int {
        val server = serverRegex.find(onClick)?.groupValues?.get(1)?.lowercase().orEmpty()
        return serverPriority[server] ?: Int.MAX_VALUE
    }

    private suspend fun processItem(
        onClick: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val match = videoRegex.find(onClick) ?: return
        val id = match.groupValues[1]
        val server = match.groupValues[2].lowercase()

        val playUrl = "$BASE_PLAYER?id=$id&sv=$server"

        runCatching {
            val response = app.get(
                playUrl,
                referer = referer,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            )

            val finalUrl = response.url

            if (finalUrl.isNotEmpty() && finalUrl != playUrl && !finalUrl.contains("pobreflixtv")) {
                loadExtractor(finalUrl, referer, subtitleCallback, callback)
            }
        }
    }
}