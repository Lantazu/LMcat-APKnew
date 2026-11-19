package com.lmcattabs.desktopcat

import android.content.Context

data class CatProfile(
    var coat: String = "pamuk",
    var mood: String = "HUNTER",
    var collar: String = "none",
    var headwear: String = "none",
    var eyewear: String = "none",
    var scale: Float = 1.25f,
    var hasPillow: Boolean = false,
    var pillowX: Float = -1f,
    var pillowY: Float = -1f,
    var autoStartOnBoot: Boolean = false
)

object CatProfileStore {
    private const val PREFS = "lmcat_tabs_prefs"

    fun load(context: Context): CatProfile {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CatProfile(
            coat = sp.getString("coat", "pamuk") ?: "pamuk",
            mood = sp.getString("mood", "HUNTER") ?: "HUNTER",
            collar = sp.getString("collar", "none") ?: "none",
            headwear = sp.getString("headwear", "none") ?: "none",
            eyewear = sp.getString("eyewear", "none") ?: "none",
            scale = sp.getFloat("scale", 1.25f).coerceIn(0.9f, 1.65f),
            hasPillow = sp.getBoolean("hasPillow", false),
            pillowX = sp.getFloat("pillowX", -1f),
            pillowY = sp.getFloat("pillowY", -1f),
            autoStartOnBoot = sp.getBoolean("autoStartOnBoot", false)
        )
    }

    fun save(context: Context, p: CatProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("coat", p.coat)
            .putString("mood", p.mood)
            .putString("collar", p.collar)
            .putString("headwear", p.headwear)
            .putString("eyewear", p.eyewear)
            .putFloat("scale", p.scale)
            .putBoolean("hasPillow", p.hasPillow)
            .putFloat("pillowX", p.pillowX)
            .putFloat("pillowY", p.pillowY)
            .putBoolean("autoStartOnBoot", p.autoStartOnBoot)
            .apply()
    }
}
