package com.doramas

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DoramasProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Doramas())
        registerExtractorAPI(VidStackDorama())
        registerExtractorAPI(VidStackDorama2())
    }
}
