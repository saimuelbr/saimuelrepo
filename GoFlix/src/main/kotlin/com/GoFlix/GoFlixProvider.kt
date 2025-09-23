package com.GoFlix

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class GoFlixProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(GoFlix())
    }
}
