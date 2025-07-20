package com.VisionCine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout

class VisionCineSettings : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val loginButton = Button(context).apply {
                text = "Login com Google"
                setOnClickListener {
                    val intent = Intent(context, VisionCineLoginActivity::class.java)
                    startActivity(intent)
                }
            }
            addView(loginButton)
        }
        setContentView(layout)
    }
} 