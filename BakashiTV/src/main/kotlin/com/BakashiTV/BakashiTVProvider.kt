package com.BakashiTV

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class BakashiTVProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(BakashiTV())
    }
}
