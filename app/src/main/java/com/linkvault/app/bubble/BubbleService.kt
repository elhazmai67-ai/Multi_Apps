package com.linkvault.app.bubble

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.linkvault.app.MainActivity
import com.linkvault.app.R
import com.linkvault.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Draws the floating button over other apps. It is a plain View (not Compose) because a service has
 * no lifecycle for Compose to hang on. Tap opens [QuickAddActivity]; drag moves it and snaps it to
 * a screen edge; tapping the small × on it hides it until the app is minimized again.
 */
class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var bubble: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val prefs by lazy { getSharedPreferences("bubble", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun screenW() = Resources.getSystem().displayMetrics.widthPixels
    private fun screenH() = Resources.getSystem().displayMetrics.heightPixels

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating button", NotificationManager.IMPORTANCE_MIN))
        // Called here as well as in onStartCommand so a very quick stop can't leave a promise unkept.
        promoteToForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            CoroutineScope(Dispatchers.IO).launch { SettingsStore(applicationContext).setFloatingButton(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        promoteToForeground()
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val off = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble_link)
            .setContentTitle("Floating button is on")
            .setContentText("Tap the button to save or download a link")
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, "Turn off", off)
            .build()
    }

    // ---- The button ----

    private fun addBubble() {
        val size = dp(56)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_link)
            setBackgroundResource(R.drawable.bubble_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(15); setPadding(pad, pad, pad, pad)
            contentDescription = "Save or download a link"
        }
        // Small × in the top-right corner: tapping it hides the button.
        val closeSize = dp(22)
        val closeBadge = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_close)
            setBackgroundResource(R.drawable.bubble_close_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(5); setPadding(pad, pad, pad, pad)
            contentDescription = "Hide floating button"
        }
        val v = FrameLayout(this).apply {
            addView(icon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(closeBadge, FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP or Gravity.END))
        }
        params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", screenW() - size).coerceIn(0, (screenW() - size).coerceAtLeast(0))
            y = prefs.getInt("y", screenH() / 3).coerceIn(0, (screenH() - size).coerceAtLeast(0))
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        v.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var dragging = false; var onClose = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y; downX = e.rawX; downY = e.rawY; dragging = false
                        // A touch that starts on the × corner is a "hide" tap, not a drag or an open.
                        onClose = e.x > view.width - closeSize - dp(4) && e.y < closeSize + dp(4)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX; val dy = e.rawY - downY
                        if (!onClose && !dragging && hypot(dx, dy) > slop) { dragging = true }
                        if (dragging) {
                            params.x = (startX + dx).toInt().coerceIn(0, (screenW() - size).coerceAtLeast(0))
                            params.y = (startY + dy).toInt().coerceIn(0, (screenH() - size).coerceAtLeast(0))
                            wm?.updateViewLayout(view, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (onClose && !dragging) hideByUser()
                        else if (!dragging) { view.performClick(); openQuickAdd() }
                        else snapToEdge(view)
                    }
                }
                return true
            }
        })
        bubble = v
        wm?.addView(v, params)
    }

    private fun hideByUser() {
        Toast.makeText(this, "Floating button hidden", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    /** The user swiped LinkVault away from recent apps: take the floating button away with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun openQuickAdd() {
        // Hide right away so a second tap can't open a second panel.
        setVisible(false)
        try {
            startActivity(Intent(this, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { setVisible(true) }
    }

    private fun snapToEdge(view: View) {
        val size = dp(56)
        val target = if (params.x + size / 2 < screenW() / 2) 0 else (screenW() - size).coerceAtLeast(0)
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                try { wm?.updateViewLayout(view, params) } catch (_: Exception) {}
            }
            start()
        }
        prefs.edit().putInt("x", target).putInt("y", params.y).apply()
    }

    private fun setVisible(visible: Boolean) {
        bubble?.post { bubble?.visibility = if (visible) View.VISIBLE else View.GONE }
    }

    override fun onDestroy() {
        bubble?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        bubble = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TURN_OFF = "com.linkvault.app.bubble.TURN_OFF"
        private const val CHANNEL = "floating_button"
        private const val NOTIF_ID = 4201
        @Volatile private var instance: BubbleService? = null

        /** The quick-add panel hides the button while it's open and brings it back after. */
        fun setBubbleVisible(visible: Boolean) { instance?.setVisible(visible) }
    }
}
