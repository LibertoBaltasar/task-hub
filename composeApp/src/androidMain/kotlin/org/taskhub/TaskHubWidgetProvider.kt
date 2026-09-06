// Widget de pantalla de inicio de Task Hub: muestra la lista de tareas
// pendientes cacheada por commonMain (ver `Platform.android.kt`,
// `updateWidgetPendingTasks`/`saveWidgetThemeToCache`) en unas
// SharedPreferences propias ("widget_cache"), fuera del flujo normal de
// Compose/Koin porque el sistema puede instanciar este componente sin que
// la app esté en ejecución.
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

/**
 * `AppWidgetProvider` del widget de tareas pendientes de Task Hub.
 * Instanciado por el sistema Android (no por Koin/Compose): al añadir el
 * widget a la pantalla de inicio, al actualizarse periódicamente según la
 * configuración del `AppWidgetProviderInfo`, y al recibir el broadcast de
 * refresco manual disparado por la app en primer plano (ver [onReceive]).
 */
class TaskHubWidgetProvider : AppWidgetProvider() {

    /** Refresco periódico del sistema: repinta cada instancia del widget en pantalla. */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * Además de los broadcasts estándar de `AppWidgetProvider` (delegados a
     * `super`), escucha el broadcast propio "org.taskhub.WIDGET_REFRESH" que
     * `updateWidgetPendingTasks` (commonMain, vía `Platform.android.kt`)
     * envía cada vez que cambia la lista de tareas pendientes cacheada —
     * así el widget se repinta al instante en vez de esperar al próximo
     * ciclo periódico del sistema.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Cuando la app envía este broadcast de refresco, actualizar todos los widgets
        if (intent.action == "org.taskhub.WIDGET_REFRESH") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, TaskHubWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

/**
 * Repinta una instancia concreta del widget con los datos cacheados en
 * SharedPreferences "widget_cache" (escritos por commonMain, no por este
 * archivo — ver la cabecera). RemoteViews no admite temas dinámicos de
 * Compose, así que el modo claro/oscuro se resuelve manualmente aquí
 * eligiendo entre dos layouts XML distintos.
 */
private fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    // Leer la preferencia de tema del widget: "light", "dark" o "system"
    val prefs = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
    val widgetTheme = prefs.getString("widget_theme", "system") ?: "system"

    val isDark = when (widgetTheme) {
        "dark" -> true
        "light" -> false
        else -> {
            // "system" — seguir el modo noche del sistema
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    val layoutId = if (isDark) R.layout.task_hub_widget_dark else R.layout.task_hub_widget
    val views = RemoteViews(context.packageName, layoutId)

    // Construir el texto de la lista de tareas — usa los datos persistidos en SharedPreferences
    val taskListText = prefs.getString("pending_tasks", "No hay tareas pendientes") ?: "Sin tareas"

    views.setTextViewText(R.id.widget_title, "📋 Tareas pendientes")
    views.setTextViewText(R.id.widget_task_list, taskListText)

    // Tocar el widget abre la app (sin deep link a una tarea concreta, a
    // diferencia de las notificaciones — este intent es solo "abrir la app").
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