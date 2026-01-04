package com.Doramas

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DoramasProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Doramas())
        registerExtractorAPI(EmbedPlayUpnsPro())
        registerExtractorAPI(EmbedPlayUpnOne())
    }
}
