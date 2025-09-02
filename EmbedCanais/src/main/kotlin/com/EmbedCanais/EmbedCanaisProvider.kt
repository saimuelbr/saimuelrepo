package com.EmbedCanais

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class EmbedCanaisProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(EmbedCanais())
    }
} 