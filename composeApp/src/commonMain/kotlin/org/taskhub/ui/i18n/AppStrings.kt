package org.taskhub.ui.i18n

/**
 * Simple string-based i18n for Spanish / English.
 * All user-visible strings go here. Use `get(key, lang)` to resolve.
 */
object AppStrings {

    private val strings: Map<String, Map<String, String>> = mapOf(
        "es" to mapOf(
            // Settings
            "settings_title" to "⚙️ Ajustes",
            "settings_notifications" to "🔔 Notificaciones",
            "settings_notifications_desc" to "Activar recordatorios de tareas",
            "settings_theme" to "🎨 Tema",
            "settings_language" to "🌐 Idioma",
            "settings_export_csv" to "📥 Exportar tareas como CSV",
            "settings_close" to "Cerrar",
            "settings_sound_vibration" to "🔊 Sonido y vibración",
            "settings_sound" to "Sonido",
            "settings_sound_desc" to "Reproducir sonido al completar una tarea",
            "settings_vibration" to "Vibración",
            "settings_vibration_desc" to "Vibrar al completar una tarea",

            // Themes
            "theme_default" to "Clásico",
            "theme_naturaleza" to "Naturaleza",
            "theme_minimal" to "Minimal",

            // Languages
            "lang_spanish" to "Español",
            "lang_english" to "English",

            // Welcome
            "welcome_title" to "Task Hub",
            "welcome_subtitle" to "Organiza las tareas de tus espacios,\ncomparte responsabilidades y gana puntos",
            "welcome_create" to "Crear espacio",
            "welcome_join" to "Unirse a un espacio",
            "welcome_my_households" to "Mis espacios",

            // Space list
            "household_list_title" to "👥 Task Hub",
            "household_list_my" to "Tus espacios",
            "household_list_create_join" to "Crear/Unirse",
            "household_list_join_other" to "+ Unirse a otro espacio",
            "household_list_no_households" to "No tienes espacios aún",
            "household_list_no_households_desc" to "Crea un espacio o únete a uno existente",
            "household_list_start" to "Comenzar",
            "household_list_select_hint" to "Toca para seleccionar/deseleccionar",
            "household_list_cancel" to "✕ Cancelar",
            "household_list_x_selected" to "seleccionados",

            // Space
            "household_back" to "← Espacios",
            "household_invite_code" to "Código de invitación",
            "household_share_code" to "Comparte este código para invitar miembros",
            "household_share" to "📤 Compartir",
            "household_invite_title" to "Código de invitación",
            "household_share_message" to "Únete a mi espacio en Task Hub: %s. Descárgala en: https://play.google.com/store/apps/details?id=org.taskhub",
            "household_share_subject" to "Invitación a Task Hub",
            "household_view_tasks" to "📋 Ver Tareas",
            "household_stats" to "📊 Estadísticas",
            "household_members" to "👥 Miembros",
            "household_no_members" to "No hay miembros aún. ¡Invita a alguien!",
            "household_delete_title" to "Eliminar espacio",
            "household_delete_confirm_1" to "¿Eliminar '%s'? Esta acción no se puede deshacer.",
            "household_delete_confirm_2" to "¿Estás completamente seguro?",
            "household_delete_confirm_2_desc" to "Se perderán todas las tareas y miembros de '%s'.",
            "household_delete_btn" to "Eliminar",
            "household_delete_yes" to "Sí, eliminar",
            "household_cancel" to "Cancelar",
            "household_close" to "Cerrar",
            "household_retry" to "Reintentar",
            "household_code_label" to "Código de invitación",

            // Tasks
            "tasks_title" to "📋 Tareas",
            "tasks_back" to "← Volver",
            "tasks_new" to "+ Nueva",
            "tasks_export_csv" to "📊 Exportar CSV",
            "tasks_export_csv_title" to "Tareas Task Hub",
            "tasks_completed_today" to "✅ Completadas hoy",
            "tasks_due_today" to "Hoy",
            "tasks_overdue" to "Vencidas",
            "tasks_empty_pending" to "🎉 ¡No hay tareas pendientes!",
            "tasks_empty_completed" to "📋 No hay tareas completadas hoy",
            "tasks_empty_mine" to "👤 No tienes tareas asignadas",
            "tasks_empty_all" to "📋 No hay tareas aún. ¡Crea la primera!",
            "tasks_error_loading" to "Error al cargar espacios",
            "tasks_retry" to "Reintentar",

            // Delete
            "delete_multiple_title" to "Eliminar %d espacio(s)",
            "delete_multiple_confirm" to "¿Eliminar %s? Esta acción no se puede deshacer.",
            "delete_multiple_sure" to "Se perderán todas las tareas y miembros de %s.",

            // Profile photo
            "profile_photo_choose" to "📷 Elegir foto",
            "profile_photo_take" to "🤳 Hacer foto",
            "profile_photo_uploading" to "Subiendo foto…",
            "profile_avatar_content_desc" to "Foto de perfil",
        ),
        "en" to mapOf(
            // Settings
            "settings_title" to "⚙️ Settings",
            "settings_notifications" to "🔔 Notifications",
            "settings_notifications_desc" to "Enable task reminders",
            "settings_theme" to "🎨 Theme",
            "settings_language" to "🌐 Language",
            "settings_export_csv" to "📥 Export tasks as CSV",
            "settings_close" to "Close",
            "settings_sound_vibration" to "🔊 Sound & Vibration",
            "settings_sound" to "Sound",
            "settings_sound_desc" to "Play sound when completing a task",
            "settings_vibration" to "Vibration",
            "settings_vibration_desc" to "Vibrate when completing a task",

            // Themes
            "theme_default" to "Classic",
            "theme_naturaleza" to "Nature",
            "theme_minimal" to "Minimal",

            // Languages
            "lang_spanish" to "Español",
            "lang_english" to "English",

            // Welcome
            "welcome_title" to "Task Hub",
            "welcome_subtitle" to "Organize the tasks in your spaces,\nshare responsibilities and earn points",
            "welcome_create" to "Create space",
            "welcome_join" to "Join a space",
            "welcome_my_households" to "My spaces",

            // Space list
            "household_list_title" to "👥 Task Hub",
            "household_list_my" to "Your spaces",
            "household_list_create_join" to "Create/Join",
            "household_list_join_other" to "+ Join another space",
            "household_list_no_households" to "No spaces yet",
            "household_list_no_households_desc" to "Create a space or join an existing one",
            "household_list_start" to "Get started",
            "household_list_select_hint" to "Tap to select/deselect",
            "household_list_cancel" to "✕ Cancel",
            "household_list_x_selected" to "selected",

            // Space
            "household_back" to "← Spaces",
            "household_invite_code" to "Invite code",
            "household_share_code" to "Share this code to invite members",
            "household_share" to "📤 Share",
            "household_invite_title" to "Invite code",
            "household_share_message" to "Join my space on Task Hub: %s. Download at: https://play.google.com/store/apps/details?id=org.taskhub",
            "household_share_subject" to "Task Hub Invitation",
            "household_view_tasks" to "📋 View Tasks",
            "household_stats" to "📊 Statistics",
            "household_members" to "👥 Members",
            "household_no_members" to "No members yet. Invite someone!",
            "household_delete_title" to "Delete space",
            "household_delete_confirm_1" to "Delete '%s'? This action cannot be undone.",
            "household_delete_confirm_2" to "Are you completely sure?",
            "household_delete_confirm_2_desc" to "All tasks and members of '%s' will be lost.",
            "household_delete_btn" to "Delete",
            "household_delete_yes" to "Yes, delete",
            "household_cancel" to "Cancel",
            "household_close" to "Close",
            "household_retry" to "Retry",
            "household_code_label" to "Invite Code",

            // Tasks
            "tasks_title" to "📋 Tasks",
            "tasks_back" to "← Back",
            "tasks_new" to "+ New",
            "tasks_export_csv" to "📊 Export CSV",
            "tasks_export_csv_title" to "Task Hub Tasks",
            "tasks_completed_today" to "✅ Completed today",
            "tasks_due_today" to "Today",
            "tasks_overdue" to "Overdue",
            "tasks_empty_pending" to "🎉 No pending tasks!",
            "tasks_empty_completed" to "📋 No tasks completed today",
            "tasks_empty_mine" to "👤 You have no assigned tasks",
            "tasks_empty_all" to "📋 No tasks yet. Create the first one!",
            "tasks_error_loading" to "Error loading spaces",
            "tasks_retry" to "Retry",

            // Delete
            "delete_multiple_title" to "Delete %d space(s)",
            "delete_multiple_confirm" to "Delete %s? This action cannot be undone.",
            "delete_multiple_sure" to "All tasks and members of %s will be lost.",

            // Profile photo
            "profile_photo_choose" to "📷 Choose photo",
            "profile_photo_take" to "🤳 Take photo",
            "profile_photo_uploading" to "Uploading photo…",
            "profile_avatar_content_desc" to "Profile photo",
        )
    )

    /**
     * Resolve a string key for the given language.
     * Falls back to Spanish if the key or language is missing.
     */
    fun get(key: String, lang: String): String {
        return strings[lang]?.get(key)
            ?: strings["es"]?.get(key)
            ?: key
    }
}
