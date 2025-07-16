package com.TopFilmes

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TopFilmesProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(TopFilmes())
    }
} 