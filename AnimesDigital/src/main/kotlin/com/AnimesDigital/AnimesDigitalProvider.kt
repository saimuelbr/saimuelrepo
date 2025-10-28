package com.AnimesDigital
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimesDigitalProvider : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimesDigital())
    }
}