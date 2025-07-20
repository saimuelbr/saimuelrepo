package com.VisionCine

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class VisionCinePlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("VisionCine", Context.MODE_PRIVATE)
        registerMainAPI(VisionCine(sharedPref))
        openSettings = {
            val intent = android.content.Intent(context, VisionCineSettings::class.java)
            context.startActivity(intent)
        }
    }
} 