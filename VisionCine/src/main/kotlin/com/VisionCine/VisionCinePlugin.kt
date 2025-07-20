package com.VisionCine

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.VisionCine.settings.SettingsFragment
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class VisionCinePlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("VisionCine", Context.MODE_PRIVATE)
        registerMainAPI(VisionCine(sharedPref))

        val activity = context as AppCompatActivity
        openSettings = {
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
} 