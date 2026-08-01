package org.taskhub

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TaskHubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // When the app sends a broadcast to refresh, update all widgets
        if (intent.action == "org.taskhub.WIDGET_REFRESH") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, TaskHubWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

private fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.task_hub_widget)

    // Build task list text — use persisted data from SharedPreferences
    val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
    val taskListText = prefs.getString("pending_tasks", "No hay tareas pendientes") ?: "Sin tareas"

    views.setTextViewText(R.id.widget_title, "📋 Tareas pendientes")
    views.setTextViewText(R.id.widget_task_list, taskListText)

    // Tap opens the app
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}