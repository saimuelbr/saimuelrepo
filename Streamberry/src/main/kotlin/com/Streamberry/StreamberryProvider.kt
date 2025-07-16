package com.Streamberry

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StreamberryProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Streamberry())
    }
} 