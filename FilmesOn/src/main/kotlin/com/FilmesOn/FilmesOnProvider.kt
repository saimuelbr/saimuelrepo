package com.FilmesOn

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FilmesOnProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(FilmesOn())
    }
}
