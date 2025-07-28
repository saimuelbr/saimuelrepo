package com.EmbedSports

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class EmbedSportsProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(EmbedSports())
    }
} 