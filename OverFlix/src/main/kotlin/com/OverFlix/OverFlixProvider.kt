package com.OverFlix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OverFlixProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(OverFlix())
    }
}