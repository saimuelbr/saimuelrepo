package com.EmbedTVOnline

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class EmbedTVOnlineProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(EmbedTVOnline())
    }
} 