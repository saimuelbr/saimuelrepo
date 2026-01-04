package com.Doramas

import com.lagradost.cloudstream3.extractors.VidStack

class EmbedPlayUpnsPro : VidStack() {
    override var name = "EmbedPlayInk"
    override var mainUrl = "https://embedplay.upns.ink"
    override var requiresReferer = true
}

class EmbedPlayUpnOne : VidStack() {
    override var name = "EmbedPlayUpnOne"
    override var mainUrl = "https://embedplay.upn.one"
    override var requiresReferer = true
}