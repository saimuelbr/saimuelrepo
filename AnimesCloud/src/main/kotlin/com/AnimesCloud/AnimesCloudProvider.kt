package com.AnimesCloud

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimesCloudProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(AnimesCloud())
    }
} 