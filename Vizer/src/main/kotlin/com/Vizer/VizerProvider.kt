package com.Vizer

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class VizerProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Vizer())
    }
} 