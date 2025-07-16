package com.PobreFlix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PobreFlixProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(PobreFlix())
    }
}