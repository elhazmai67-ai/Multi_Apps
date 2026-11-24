package com.linkvault.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

enum class OpenMode(val label: String, val description: String) {
    SMART("Smart", "Open in the matching app, otherwise the browser"),
    BROWSER("Browser", "Always open in your default browser"),
    APP("App when available", "Prefer the native app, fall back to the browser");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.name == k } ?: SMART
    }
}

object LinkOpener {
    /** Returns true if something was launched. Never throws. */
    fun open(context: Context, url: String, platform: Platform, mode: OpenMode): Boolean {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return false }
        val newTask = if (context !is android.app.Activity) Intent.FLAG_ACTIVITY_NEW_TASK else 0

        if (mode != OpenMode.BROWSER) {
            for (pkg in nativePackagesFor(platform, url)) {
                if (tryStart(context, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(newTask))) return true
            }
        }
        // Generic handler. In Smart mode plain ACTION_VIEW lets Android route verified App Links to
        // the right app. In Browser mode the APP_BROWSER selector forces the default browser.
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(newTask)
        if (mode == OpenMode.BROWSER) {
            view.addCategory(Intent.CATEGORY_BROWSABLE)
            view.selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        if (tryStart(context, view)) return true
        // Last resort: a plain VIEW without the selector.
        return tryStart(context, Intent(Intent.ACTION_VIEW, uri).addFlags(newTask))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

    fun share(context: Context, url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        try { context.startActivity(Intent.createChooser(send, "Share link")) } catch (_: Exception) {}
    }
}
