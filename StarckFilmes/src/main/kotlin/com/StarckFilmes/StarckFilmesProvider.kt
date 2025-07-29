package com.StarckFilmes

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StarckFilmesProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(StarckFilmes())
    }
} 