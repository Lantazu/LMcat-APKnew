package com.lmcattabs.desktopcat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val profile = CatProfileStore.load(context)
        if (profile.autoStartOnBoot && Settings.canDrawOverlays(context)) {
            val svc = Intent(context, CatOverlayService::class.java).setAction(CatOverlayService.ACTION_START)
            ContextCompat.startForegroundService(context, svc)
        }
    }
}
