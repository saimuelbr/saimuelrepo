package com.doramas

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VidStackDorama2 : VidStackDorama() {
    override var name = "Vidstack"
    override var mainUrl = "https://embedplay.upns.pro"
}

open class VidStackDorama : ExtractorApi() {
    override var name = "Vidstack"
    override var mainUrl = "https://embedplay.upn.one"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers =
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0")
        val hash = url.substringAfterLast("#").substringAfter("/")
        val baseurl = getBaseUrl(url)

        val encoded = app.get("$baseurl/api/v1/video?id=$hash", headers = headers).text.trim()

        val key = "kiemtienmua911ca"
        val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

        val decryptedText = ivList.firstNotNullOfOrNull { iv ->
            try {
                AesHelper.decryptAES(encoded, key, iv)
            } catch (e: Exception) {
                null
            }
        } ?: throw Exception("Failed to decrypt with all IVs")

        val m3u8 = Regex("\"source\":\"(.*?)\"").find(decryptedText)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/") ?: ""

        val subtitleRegex = Regex("\"subtitle\"\\s*:\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)

        val subtitleBlock = subtitleRegex.find(decryptedText)
            ?.groupValues?.get(1) ?: ""

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8,
                url,
                Qualities.P1080.value,
                type = ExtractorLinkType.M3U8,
            )
        )

        subtitleBlock.let { block ->
            Regex("\"(\\w+)\":\"(.*?)\"")
                .findAll(block)
                .forEach { match ->
                    val lang = match.groupValues[1]
                    val leg = match.groupValues[2].replace("\\/", "/")
                    subtitleCallback.invoke(SubtitleFile(lang, "$mainUrl$leg"))
                }
        }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (e: Exception) {
            Log.e("Vidstack", "getBaseUrl fallback: ${e.message}")
            mainUrl
        }
    }
}

object AesHelper {
    private const val TRANSFORMATION = "AES/CBC/PKCS5PADDING"

    fun decryptAES(inputHex: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val decryptedBytes = cipher.doFinal(inputHex.hexToByteArray())
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
