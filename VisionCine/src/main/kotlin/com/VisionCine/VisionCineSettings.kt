package com.VisionCine

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

class VisionCineSettings : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("VisionCine", Context.MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tokenInput = EditText(this).apply {
            hint = "Cole o token aqui"
            setText(prefs.getString("token", ""))
        }

        val loginButton = Button(this).apply {
            text = "Login com Google"
        }

        val webView = WebView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        loginButton.setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://accounts.google.com/o/oauth2/auth/oauthchooseaccount?response_type=code&client_id=101797831701-6cp2r9rmuoimlim0hb5caft0ufo9vgq5.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fpixerplay-61ab9.firebaseapp.com%2F__%2Fauth%2Fhandler&scope=openid%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email%20profile&context_uri=https%3A%2F%2Fvisioncine.live&service=lso&o2v=1&flowName=GeneralOAuthFlow")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url ?: "")
                val token = cookies?.split(";")?.map { it.trim() }?.find { it.startsWith("PHPSESSID=") }?.removePrefix("PHPSESSID=")
                if (!token.isNullOrEmpty()) {
                    tokenInput.setText(token)
                    prefs.edit().putString("token", token).apply()
                    webView.visibility = View.GONE
                }
            }
        }

        val saveButton = Button(this).apply {
            text = "Salvar Token"
            setOnClickListener {
                val token = tokenInput.text.toString().trim()
                prefs.edit().putString("token", token).apply()
                finish()
            }
        }

        val resetButton = Button(this).apply {
            text = "Resetar Token"
            setOnClickListener {
                prefs.edit().remove("token").apply()
                tokenInput.setText("")
            }
        }

        layout.addView(tokenInput)
        layout.addView(loginButton)
        layout.addView(webView)
        layout.addView(saveButton)
        layout.addView(resetButton)

        setContentView(layout)
    }
}
