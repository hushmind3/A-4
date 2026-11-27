package com.dominion.mobile.platform.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R

/**
 * Home-screen widgets, matching the original client's 4x2 and 2x1 widgets.
 * Actual quick-entry actions; private conversation content is not exposed on widgets.
 */
open class DominionWidgetProvider(private val layout: Int) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_title, "Dominion")
            views.setTextViewText(R.id.widget_subtitle, "채팅 · 음성 비서")
            views.setOnClickPendingIntent(
                R.id.widget_root,
                activityIntent(context, ACTION_OPEN_CHAT, 100 + appWidgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_chat,
                activityIntent(context, ACTION_OPEN_CHAT, 200 + appWidgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_call,
                activityIntent(context, ACTION_START_CALL, 300 + appWidgetId)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun activityIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.dominion.mobile.action.OPEN_CHAT"
        const val ACTION_START_CALL = "com.dominion.mobile.action.START_CALL"

        fun refresh(context: Context) {
            listOf(
                HomeScreenWidgetProvider::class.java,
                HomeScreenWidget2x1Provider::class.java
            ).forEach { provider ->
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, provider)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(
                        Intent(context, provider).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                    )
                }
            }
        }
    }
}

class HomeScreenWidgetProvider : DominionWidgetProvider(R.layout.widget_dominion)

class HomeScreenWidget2x1Provider : DominionWidgetProvider(R.layout.widget_dominion_small)
