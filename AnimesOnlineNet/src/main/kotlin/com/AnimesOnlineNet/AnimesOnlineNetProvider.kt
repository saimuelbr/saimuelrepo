package com.AnimesOnlineNet

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimesOnlineNetProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimesOnlineNet())
    }
}
