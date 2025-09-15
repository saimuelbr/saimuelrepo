package com.Anroll

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnrollProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Anroll())
    }
}


