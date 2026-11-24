package com.linkvault.app.bubble

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Starts and stops the floating button. The app calls this as it goes to the background and comes back. */
object BubbleController {
    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (!canDraw(context)) return
        try {
            ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, BubbleService::class.java))
        } catch (_: Exception) {
            // Android refused a background start; the button simply doesn't appear this time.
        }
    }

    fun hide(context: Context) {
        context.applicationContext.stopService(Intent(context.applicationContext, BubbleService::class.java))
    }
}
