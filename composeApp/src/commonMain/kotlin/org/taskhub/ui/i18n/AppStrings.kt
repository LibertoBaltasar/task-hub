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

            // Cuenta
            "settings_account_title" to "Cuenta",
            "settings_account_connected_prefix" to "✅ Conectado como %s",
            "settings_account_cloud_note" to "Tus datos se guardan en la nube y se recuperan si reinstalas.",
            "settings_account_sign_out" to "Cerrar sesión",
            "settings_account_connecting" to "Conectando con Google...",
            "settings_account_retry" to "Reintentar",
            "settings_account_no_session" to "Sin sesión: tus datos solo se guardan en este dispositivo.",
            "settings_account_sign_in_google" to "Iniciar sesión con Google",
            "settings_account_edit_profile" to "✏️ Editar perfil",

            // Tema del widget
            "settings_widget_theme_title" to "Tema del widget",
            "widget_theme_light" to "Claro",
            "widget_theme_dark" to "Oscuro",
            "widget_theme_system" to "Sistema",

            // Google Calendar
            "calendar_settings_title" to "📅 Google Calendar",
            "calendar_sync_toggle_label" to "Sincronizar tareas con Google Calendar",
            "calendar_sync_toggle_desc" to "Crea un evento cuando te asignan una tarea con fecha",
            "calendar_link_hint" to "Vincula tu cuenta de Google para activar la sincronización.",
            "calendar_link_button" to "Vincular cuenta de Google",
            "calendar_linking" to "Vinculando…",
            "calendar_link_error" to "No se pudo vincular la cuenta de Google",
            "calendar_linked_as" to "Vinculada: %s",
            "calendar_unlink_button" to "Desvincular",
            "calendar_independent_note" to "Cada espacio usa su propio calendario en Google (p. ej. \"Tareas personal\", \"Tareas Casa\").",
            "calendar_status_no_due_date" to "📅 Sin fecha — no aparece en el calendario",
            "calendar_status_synced" to "✅ Sincronizada con Google Calendar",
            "calendar_status_pending" to "🕓 Pendiente de sincronizar",
            "calendar_status_sync_now" to "Sincronizar ahora",
            "calendar_status_syncing" to "Sincronizando…",
            "calendar_status_not_linked" to "🔗 Cuenta de Google no vinculada",
            "calendar_status_link_cta" to "Vincular cuenta",
            "calendar_status_sync_disabled" to "⏸️ Sincronización desactivada",
            "calendar_status_enable_cta" to "Activar",

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
            "household_leave_title" to "Salir del espacio",
            "household_leave_confirm" to "¿Desvincularte de '%s'? Dejarás de verlo en tu dispositivo. Si eres el último miembro, el espacio se eliminará.",
            "household_leave_btn" to "Salir",
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

            // Recurrence
            "recurrence_label" to "Recurrencia",
            "recurrence_none" to "Ninguna",
            "recurrence_once" to "Una vez",
            "recurrence_daily" to "Diaria",
            "recurrence_weekly" to "Semanal",
            "recurrence_monthly" to "Mensual",
            "recurrence_every_day" to "Cada día",
            "recurrence_every_week" to "Cada semana",
            "recurrence_every_month" to "Cada mes",
            "recurrence_days_label" to "Días de repetición",
            "recurrence_day_of_month_label" to "Día del mes",
            "recurrence_day_monday" to "Lunes",
            "recurrence_day_tuesday" to "Martes",
            "recurrence_day_wednesday" to "Miércoles",
            "recurrence_day_thursday" to "Jueves",
            "recurrence_day_friday" to "Viernes",
            "recurrence_day_saturday" to "Sábado",
            "recurrence_day_sunday" to "Domingo",

            // Delete
            "delete_multiple_title" to "Eliminar %d espacio(s)",
            "delete_multiple_confirm" to "¿Eliminar %s? Esta acción no se puede deshacer.",
            "delete_multiple_sure" to "Se perderán todas las tareas y miembros de %s.",

            // Profile photo
            "profile_avatar_content_desc" to "Foto de perfil",

            // Chat / Messages
            "messages_title" to "💬 Mensajes",
            "messages_send" to "Enviar",
            "messages_hint" to "Escribe un mensaje…",
            "messages_empty" to "Aún no hay mensajes. ¡Escribe el primero!",
            "messages_error_loading" to "Error al cargar los mensajes",
            "messages_error_sending" to "Error al enviar el mensaje",
            "messages_refresh" to "Actualizar mensajes",

            // Agradecer / Donar puntos entre miembros
            "appreciate_action" to "👍 Agradecer",
            "donate_action" to "🎁 Donar",
            "appreciate_dialog_title" to "Agradecer a",
            "donate_dialog_title" to "Donar a",
            "appreciate_dialog_remaining_label" to "Presupuesto restante esta semana",
            "donate_dialog_balance_label" to "Tu saldo actual",
            "transfer_amount_label" to "Puntos a enviar",
            "transfer_confirm" to "Confirmar",
            "transfer_cancel" to "Cancelar",
            "transfer_points_suffix" to "pts",
            "appreciate_success" to "¡Agradecimiento enviado!",
            "donate_success" to "¡Donación enviada!",
            "appreciate_no_budget" to "Sin presupuesto para agradecer esta semana",
            "donate_no_balance" to "No tienes puntos para donar",
            "transfer_error_self" to "No puedes hacerlo contigo mismo",
            "transfer_error_invalid_amount" to "Importe inválido",
            "transfer_error_member_not_found" to "Miembro no encontrado",
            "appreciate_error_limit" to "Límite semanal alcanzado",
            "donate_error_insufficient_balance" to "Saldo insuficiente",
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

            // Account
            "settings_account_title" to "Account",
            "settings_account_connected_prefix" to "✅ Signed in as %s",
            "settings_account_cloud_note" to "Your data is saved to the cloud and recovered if you reinstall.",
            "settings_account_sign_out" to "Sign out",
            "settings_account_connecting" to "Connecting to Google...",
            "settings_account_retry" to "Retry",
            "settings_account_no_session" to "No session: your data is only saved on this device.",
            "settings_account_sign_in_google" to "Sign in with Google",
            "settings_account_edit_profile" to "✏️ Edit profile",

            // Widget theme
            "settings_widget_theme_title" to "Widget theme",
            "widget_theme_light" to "Light",
            "widget_theme_dark" to "Dark",
            "widget_theme_system" to "System",

            // Google Calendar
            "calendar_settings_title" to "📅 Google Calendar",
            "calendar_sync_toggle_label" to "Sync tasks with Google Calendar",
            "calendar_sync_toggle_desc" to "Creates an event whenever you're assigned a task with a due date",
            "calendar_link_hint" to "Link your Google account to turn on syncing.",
            "calendar_link_button" to "Link Google account",
            "calendar_linking" to "Linking…",
            "calendar_link_error" to "Couldn't link your Google account",
            "calendar_linked_as" to "Linked: %s",
            "calendar_unlink_button" to "Unlink",
            "calendar_independent_note" to "Each space uses its own Google calendar (e.g. \"Personal tasks\", \"Home tasks\").",
            "calendar_status_no_due_date" to "📅 No due date — won't show up on the calendar",
            "calendar_status_synced" to "✅ Synced with Google Calendar",
            "calendar_status_pending" to "🕓 Pending sync",
            "calendar_status_sync_now" to "Sync now",
            "calendar_status_syncing" to "Syncing…",
            "calendar_status_not_linked" to "🔗 Google account not linked",
            "calendar_status_link_cta" to "Link account",
            "calendar_status_sync_disabled" to "⏸️ Sync turned off",
            "calendar_status_enable_cta" to "Turn on",

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
            "household_leave_title" to "Leave space",
            "household_leave_confirm" to "Unlink from '%s'? You'll stop seeing it on this device. If you're the last member, the space will be deleted.",
            "household_leave_btn" to "Leave",
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

            // Recurrence
            "recurrence_label" to "Recurrence",
            "recurrence_none" to "None",
            "recurrence_once" to "Once",
            "recurrence_daily" to "Daily",
            "recurrence_weekly" to "Weekly",
            "recurrence_monthly" to "Monthly",
            "recurrence_every_day" to "Every day",
            "recurrence_every_week" to "Every week",
            "recurrence_every_month" to "Every month",
            "recurrence_days_label" to "Repeat days",
            "recurrence_day_of_month_label" to "Day of month",
            "recurrence_day_monday" to "Monday",
            "recurrence_day_tuesday" to "Tuesday",
            "recurrence_day_wednesday" to "Wednesday",
            "recurrence_day_thursday" to "Thursday",
            "recurrence_day_friday" to "Friday",
            "recurrence_day_saturday" to "Saturday",
            "recurrence_day_sunday" to "Sunday",

            // Delete
            "delete_multiple_title" to "Delete %d space(s)",
            "delete_multiple_confirm" to "Delete %s? This action cannot be undone.",
            "delete_multiple_sure" to "All tasks and members of %s will be lost.",

            // Profile photo
            "profile_avatar_content_desc" to "Profile photo",

            // Chat / Messages
            "messages_title" to "💬 Messages",
            "messages_send" to "Send",
            "messages_hint" to "Write a message…",
            "messages_empty" to "No messages yet. Write the first one!",
            "messages_error_loading" to "Error loading messages",
            "messages_error_sending" to "Error sending message",
            "messages_refresh" to "Refresh messages",

            // Appreciate / Donate points between members
            "appreciate_action" to "👍 Thank",
            "donate_action" to "🎁 Donate",
            "appreciate_dialog_title" to "Thank",
            "donate_dialog_title" to "Donate to",
            "appreciate_dialog_remaining_label" to "Remaining budget this week",
            "donate_dialog_balance_label" to "Your current balance",
            "transfer_amount_label" to "Points to send",
            "transfer_confirm" to "Confirm",
            "transfer_cancel" to "Cancel",
            "transfer_points_suffix" to "pts",
            "appreciate_success" to "Thanks sent!",
            "donate_success" to "Donation sent!",
            "appreciate_no_budget" to "No budget left to thank anyone this week",
            "donate_no_balance" to "You have no points to donate",
            "transfer_error_self" to "You can't do this to yourself",
            "transfer_error_invalid_amount" to "Invalid amount",
            "transfer_error_member_not_found" to "Member not found",
            "appreciate_error_limit" to "Weekly limit reached",
            "donate_error_insufficient_balance" to "Insufficient balance",
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
