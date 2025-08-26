package com.DonghuaNoSekai

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DonghuaNoSekaiProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(DonghuaNoSekai())
    }
}
