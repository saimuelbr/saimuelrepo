package com.MegaFlix

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import android.content.Context

@CloudstreamPlugin
class MegaFlixProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(MegaFlix())
    }
}
