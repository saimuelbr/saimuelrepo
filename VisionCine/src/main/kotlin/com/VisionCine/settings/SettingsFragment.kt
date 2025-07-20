package com.VisionCine.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast

class SettingsFragment(
    private val plugin: Any,
    private val sharedPref: SharedPreferences,
) : BottomSheetDialogFragment() {
    private val res by lazy {
        val clazz = plugin.javaClass
        val getRes = clazz.methods.find { it.name == "getResources" }
        getRes?.invoke(plugin) as? android.content.res.Resources ?: throw Exception("Unable to read resources")
    }

    @SuppressLint("DiscouragedApi")
    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", "com.VisionCine")
        return this.findViewById(id)
    }

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val id = res.getIdentifier("settings_fragment", "layout", "com.VisionCine")
        return inflater.inflate(id, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tokenInput = view.findView<EditText>("tokenInput")
        val addButton = view.findView<Button>("addButton")
        val resetButton = view.findView<Button>("resetButton")
        val loginButton = view.findView<Button>("loginButton")
        val webView = view.findView<WebView>("authWebView")

        val savedToken = sharedPref.getString("token", null)
        if (!savedToken.isNullOrEmpty()) {
            tokenInput.setText(savedToken)
        }

        setupWebView(webView)

        loginButton.setOnClickListener {
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://accounts.google.com/o/oauth2/auth/oauthchooseaccount?response_type=code&client_id=101797831701-6cp2r9rmuoimlim0hb5caft0ufo9vgq5.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fpixerplay-61ab9.firebaseapp.com%2F__%2Fauth%2Fhandler&scope=openid%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email%20profile&context_uri=https%3A%2F%2Fvisioncine.live&service=lso&o2v=1&flowName=GeneralOAuthFlow")
        }

        addButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isNotEmpty()) {
                sharedPref.edit()?.apply {
                    putString("token", token)
                    apply()
                }
                showToast("Token salvo com sucesso. Reinicie o app.")
                dismiss()
            } else {
                showToast("Por favor, insira um token válido")
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit()?.apply {
                remove("token")
                apply()
            }
            tokenInput.setText("")
            showToast("Token resetado com sucesso. Reinicie o app.")
            dismiss()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url ?: "")

                val token = cookies?.split(";")
                    ?.map { it.trim() }
                    ?.find { it.startsWith("PHPSESSID=") }
                    ?.removePrefix("PHPSESSID=")

                if (!token.isNullOrEmpty() && view != null) {
                    val finalToken = token

                    activity?.runOnUiThread {
                        val tokenInput = requireView().findViewById<EditText>(
                            res.getIdentifier("tokenInput", "id", "com.VisionCine")
                        )
                        tokenInput.setText(finalToken)

                        sharedPref.edit()?.apply {
                            putString("token", finalToken)
                            apply()
                        }
                        showToast("Login realizado com sucesso!")
                        webView.visibility = View.GONE
                    }
                }
            }
        }
    }
} 