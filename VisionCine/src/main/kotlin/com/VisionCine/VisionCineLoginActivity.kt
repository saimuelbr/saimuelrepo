package com.VisionCine

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.app
import okhttp3.HttpUrl
import okhttp3.Cookie

class VisionCineLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.contains("/profiles") || url.contains("/token")) {
                    setResult(Activity.RESULT_OK)
                    finish()
                    return true
                }
                return false
            }
        }
        webView.loadUrl("https://accounts.google.com/o/oauth2/auth/oauthchooseaccount?response_type=code&client_id=101797831701-6cp2r9rmuoimlim0hb5caft0ufo9vgq5.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fpixerplay-61ab9.firebaseapp.com%2F__%2Fauth%2Fhandler&scope=openid%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email%20profile&context_uri=https%3A%2F%2Fvisioncine.live&service=lso&o2v=1&flowName=GeneralOAuthFlow")
    }
} 