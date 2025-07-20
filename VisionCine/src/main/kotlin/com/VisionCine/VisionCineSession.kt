package com.VisionCine

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

object VisionCineSession : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookieManager = CookieManager.getInstance()
        for (cookie in cookies) {
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieString.split(";").mapNotNull {
            Cookie.parse(url, it.trim())
        }
    }

    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun isLoggedIn(url: HttpUrl): Boolean {
        return loadForRequest(url).any { it.name == "PHPSESSID" }
    }
} 