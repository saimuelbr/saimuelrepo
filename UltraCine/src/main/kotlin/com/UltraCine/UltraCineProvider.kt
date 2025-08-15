package com.UltraCine

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class UltraCineProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(UltraCine())
        registerExtractorAPI(EmbedPlayUpnsPro())
        registerExtractorAPI(EmbedPlayUpnOne())
    }
}
