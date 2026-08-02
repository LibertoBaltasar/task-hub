package org.taskhub

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
    // Read widget theme preference: "light", "dark", or "system"
    val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
    val widgetTheme = prefs.getString("widget_theme", "system") ?: "system"

    val isDark = when (widgetTheme) {
        "dark" -> true
        "light" -> false
        else -> {
            // "system" — follow system night mode
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    val layoutId = if (isDark) R.layout.task_hub_widget_dark else R.layout.task_hub_widget
    val views = RemoteViews(context.packageName, layoutId)

    // Build task list text — use persisted data from SharedPreferences
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