package com.NovelasFlix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class NovelasFlixProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(NovelasFlix())
    }
} 