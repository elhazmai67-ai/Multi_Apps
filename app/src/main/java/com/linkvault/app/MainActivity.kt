package com.linkvault.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.linkvault.app.data.ThemeMode
import com.linkvault.app.ui.theme.LinkVaultTheme
import com.linkvault.app.util.UrlUtils

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private val sharedSession = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until settings are read, so a locked app never flashes its content.
        installSplashScreen().setKeepOnScreenCondition { vm.settings.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsState()
            val mode = settings?.theme ?: ThemeMode.LIGHT

            // Keep the XML window theme, status-bar icons and Compose theme in agreement.
            LaunchedEffect(mode) {
                AppCompatDelegate.setDefaultNightMode(
                    when (mode) {
                        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    }
                )
                val isDark = { res: android.content.res.Resources ->
                    when (mode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM ->
                            (res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, isDark)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Hide content from the recent-apps thumbnail and screenshots while App Lock is on.
            LaunchedEffect(settings?.appLockEnabled) {
                if (settings?.appLockEnabled == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            LinkVaultTheme(mode) {
                Box {
                    // Wait for the first settings read so a locked app never flashes its content.
                    if (settings != null) LinkVaultApp(vm, sharedSession)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vm.onBackgrounded()
            // Minimized: show the floating button, if the user turned it on and allowed it.
            if (!isFinishing && vm.settings.value?.floatingButton == true) com.linkvault.app.bubble.BubbleController.show(applicationContext)
        }
    }

    override fun onDestroy() {
        // Closed for real (back out of the app), not just rotated: no floating button left behind.
        if (isFinishing) com.linkvault.app.bubble.BubbleController.hide(this)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        com.linkvault.app.bubble.BubbleController.hide(this)   // the button is only for when the app is away
        vm.onForegrounded()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = UrlUtils.extractUrl(text) ?: text?.trim()?.takeIf { it.isNotEmpty() && !it.contains(' ') }
                if (url != null) {
                    sharedSession.value = true
                    vm.pendingNav = PendingNav.Add(url)
                } else {
                    vm.toast("That didn't contain a link LinkVault can save.")
                }
            }
            "com.linkvault.app.ADD" -> vm.pendingNav = PendingNav.Add(null)
            "com.linkvault.app.SEARCH" -> vm.pendingNav = PendingNav.Search
            "com.linkvault.app.FAVORITES" -> vm.pendingNav = PendingNav.Favorites
            "com.linkvault.app.PINNED" -> vm.pendingNav = PendingNav.Pinned
            "com.linkvault.app.DOWNLOADS" -> vm.pendingNav = PendingNav.Downloads
        }
        // Consume so a rotation or re-launch doesn't repeat the action.
        intent?.action = Intent.ACTION_MAIN
    }
}
