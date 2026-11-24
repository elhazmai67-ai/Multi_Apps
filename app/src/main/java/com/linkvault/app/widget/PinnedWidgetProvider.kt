package com.linkvault.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.linkvault.app.R
import com.linkvault.app.data.AppDatabase
import com.linkvault.app.data.SettingsStore
import com.linkvault.app.util.LinkOpener
import com.linkvault.app.util.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PinnedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { ids.forEach { render(context, manager, it) } } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        val pending = goAsync()
        scope.launch { try { render(context, manager, id) } finally { pending.finish() } }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val itemIds = intArrayOf(R.id.item1, R.id.item2, R.id.item3, R.id.item4)

        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            scope.launch {
                val mgr = AppWidgetManager.getInstance(app)
                val ids = mgr.getAppWidgetIds(ComponentName(app, PinnedWidgetProvider::class.java))
                ids.forEach { render(app, mgr, it) }
            }
        }

        private suspend fun render(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val pinned = AppDatabase.get(context).linkDao().pinnedSnapshot(4)
            val views = RemoteViews(context.packageName, R.layout.widget_pinned)
            views.setViewVisibility(R.id.widget_empty, if (pinned.isEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_row1, if (pinned.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_row2, if (pinned.size > 2) View.VISIBLE else View.GONE)

            itemIds.forEachIndexed { i, viewId ->
                val link = pinned.getOrNull(i)
                if (link == null) {
                    views.setViewVisibility(viewId, if (i < 2 && pinned.isNotEmpty()) View.INVISIBLE else View.GONE)
                } else {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, link.title)
                    val intent = Intent(context, WidgetOpenActivity::class.java)
                        .putExtra(WidgetOpenActivity.EXTRA_ID, link.id)
                        .setData(android.net.Uri.parse("linkvault://open/${link.id}"))
                    views.setOnClickPendingIntent(
                        viewId,
                        PendingIntent.getActivity(context, link.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
            }
            // Tapping the title opens the app.
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                views.setOnClickPendingIntent(
                    R.id.widget_title,
                    PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            }
            mgr.updateAppWidget(widgetId, views)
        }
    }
}

/** Invisible trampoline: opens the link per the user's setting, records usage, finishes. */
class WidgetOpenActivity : ComponentActivity() {
    companion object { const val EXTRA_ID = "link_id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        lifecycleScope.launch {
            val link = if (id > 0) AppDatabase.get(this@WidgetOpenActivity).linkDao().get(id) else null
            if (link != null) {
                val mode = SettingsStore(this@WidgetOpenActivity).flow.first().openMode
                LinkOpener.open(this@WidgetOpenActivity, link.url, Platform.fromKey(link.platform), mode)
                AppDatabase.get(this@WidgetOpenActivity).linkDao().recordOpen(id, System.currentTimeMillis())
            }
            finish()
        }
    }
}
