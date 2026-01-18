package com.BetterAnime

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class BetterAnimeProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(BetterAnime())
    }
}