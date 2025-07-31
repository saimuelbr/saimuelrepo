package com.AnimeFHD

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeFHDProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimeFHD())
    }
} 