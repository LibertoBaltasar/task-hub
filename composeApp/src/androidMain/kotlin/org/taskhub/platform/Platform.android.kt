package org.taskhub.platform

import android.content.Context
import android.content.Intent
import org.taskhub.TaskHubWidgetProvider
import org.taskhub.GoogleSignInHelper

actual fun shareText(text: String, title: String) {
    val context = AndroidContextHolder.context ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

actual fun saveWidgetThemeToCache(theme: String) {
    val context = AndroidContextHolder.context ?: return
    context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        .edit()
        .putString("widget_theme", theme)
        .apply()
}

actual fun updateWidgetPendingTasks(taskList: String) {
    val context = AndroidContextHolder.context ?: return
    context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        .edit()
        .putString("pending_tasks", taskList)
        .apply()
    val intent = Intent(context, TaskHubWidgetProvider::class.java).apply {
        action = "org.taskhub.WIDGET_REFRESH"
    }
    context.sendBroadcast(intent)
}

actual fun launchGoogleSignIn() {
    val context = AndroidContextHolder.context ?: return
    GoogleSignInHelper.launch(context)
}

/** Simple static context holder set from MainActivity. */
object AndroidContextHolder {
    @Volatile
    var context: Context? = null
}