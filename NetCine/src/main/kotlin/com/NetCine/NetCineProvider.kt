package com.NetCine

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class NetCineProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(NetCine())
    }
}