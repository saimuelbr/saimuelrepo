package com.VisionCine

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class VisionCineProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(VisionCine())
    }
} 