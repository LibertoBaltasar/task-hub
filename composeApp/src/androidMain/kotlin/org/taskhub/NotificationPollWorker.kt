package org.taskhub

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import org.taskhub.network.FirestoreClient
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.MemberRepository
import org.taskhub.network.NotificationRepository
import org.taskhub.network.firestoreBaseUrl
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore
import org.taskhub.storage.TaskCache
import org.taskhub.ui.i18n.NotificationText

private const val TAG = "NotificationPoll"
private const val NOTIFICATION_MAX_AGE_MILLIS = 90L * 24 * 60 * 60 * 1000

/**
 * Sondeo periódico (WorkManager, ver [TaskHubApplication]) de
 * `households/{id}/notifications` para los hogares guardados localmente —
 * el único mecanismo de entrega real de "tarea asignada"/"mensaje nuevo" al
 * dispositivo, dado que no hay backend propio ni Cloud Functions que puedan
 * emitir un push FCM dirigido con el token que ya se sube a `users/{uid}`
 * (panel de notificaciones 2026-09-05, gap B — veredicto de arquitectura:
 * polling en vez de push real, ver docs/review-panel-expertos-notificaciones-2026-09-05.md).
 *
 * Construye sus propias dependencias en vez de usar Koin: el contenedor de
 * `KoinApplication` en `App.kt` solo vive mientras el árbol de Compose está
 * en pantalla, y este Worker debe poder ejecutarse con la Activity cerrada o
 * el proceso recién arrancado por el propio sistema para este trabajo.
 * `Settings()` (multiplatform-settings-no-arg) es segura de invocar aquí: se
 * auto-inicializa vía androidx.startup con el mismo `Application` de toda la
 * app, así que lee/escribe las MISMAS SharedPreferences que `AppModule`.
 */
class NotificationPollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings()
        val settingsStore = SettingsStore(settings)

        // Respeta el interruptor de "Notificaciones" del propio dispositivo
        // (SettingsSheet) — no podemos respetar el del DESTINATARIO de una
        // asignación/mensaje ajeno (es un ajuste local, no de Firestore), solo
        // el de este dispositivo para lo que ESTE dispositivo muestra.
        if (!settingsStore.isNotificationsEnabled()) return Result.success()

        // Si el permiso de notificaciones está denegado, NO tocamos ningún
        // estado de sondeo — con el permiso denegado no se mostraría nada de
        // todos modos, y si avanzáramos el estado igual, esas notificaciones
        // se perderían para siempre en cuanto el usuario reactive el permiso
        // más tarde (panel de notificaciones 2026-09-05, QA). Se reintenta
        // el hogar entero en el siguiente ciclo.
        if (!NotificationHelper.canShowNotifications(applicationContext)) return Result.success()

        val householdStore = HouseholdStore(settings)
        val households = householdStore.getSavedHouseholds()
        if (households.isEmpty()) return Result.success()

        val taskCache = TaskCache(settings)
        val firestoreClient = FirestoreClient(
            apiKey = FirestoreRepository.DEFAULT_API_KEY,
            settingsStore = settingsStore
        )
        val memberRepository = MemberRepository(firestoreBaseUrl(), firestoreClient, taskCache)
        val notificationRepository = NotificationRepository(firestoreBaseUrl(), firestoreClient)
        val lang = settingsStore.getLanguage()

        for (household in households) {
            try {
                pollHousehold(household.id, firestoreClient, memberRepository, notificationRepository, settingsStore, lang)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best-effort: un hogar sin red/permiso (p.ej. lo acaban de
                // expulsar) no debe impedir sondear el resto.
                Log.w(TAG, "Fallo sondeando hogar ${household.id}: ${e.message}")
            }
        }
        return Result.success()
    }

    private suspend fun pollHousehold(
        householdId: String,
        firestoreClient: FirestoreClient,
        memberRepository: MemberRepository,
        notificationRepository: NotificationRepository,
        settingsStore: SettingsStore,
        lang: String
    ) {
        // Verificación de pertenencia REAL antes de confiar en
        // `resolveCurrentMember`: su fallback interno ("si no hay match por
        // identidad, usa el primer miembro existente") está pensado para el
        // primer arranque de un hogar recién creado, pero un sondeo
        // automático en segundo plano lo ejercita para CUALQUIER hogar
        // guardado localmente, incluido uno donde a este usuario ya lo
        // expulsaron (su propio `member` queda con `leftAt` y desaparece de
        // `getMembers`) — sin esta comprobación, el Worker heredaría la
        // identidad del "primer miembro" (uno REAL, ajeno) y mostraría en
        // este dispositivo notificaciones dirigidas a otra persona (fuga de
        // privacidad, panel de notificaciones 2026-09-05, QA, CRÍTICO). El
        // resto de la app no sondea hogares en segundo plano sin que el
        // usuario abra la pantalla, así que ese código compartido no se toca
        // aquí — el arreglo se acota a este Worker nuevo.
        // ensureAuth() primero — igual que MemberRepository.resolveCurrentMemberUncached
        // — para que currentUserIdentities() incluya el UID real de esta
        // sesión y no solo el persistido de una sesión anterior.
        firestoreClient.ensureAuth()
        val members = memberRepository.getMembers(householdId)
        val identities = firestoreClient.currentUserIdentities()
        val isRealMember = members.any { it.userId != null && it.userId in identities }
        if (!isRealMember) return

        val memberId = memberRepository.resolveCurrentMember(householdId)
        if (memberId.isBlank()) return

        val all = notificationRepository.getNotifications(householdId)

        // Purga best-effort de notificaciones LEÍDAS con más de 90 días —
        // reutiliza el `all` ya traído arriba (sin fetch extra) y solo se
        // ejecuta para hogares donde ya se comprobó pertenencia real
        // (mismo criterio que el resto de este Worker, panel de
        // notificaciones 2026-09-05, IMPORTANTE).
        notificationRepository.purgeOldRead(householdId, all, maxAgeMillis = NOTIFICATION_MAX_AGE_MILLIS)

        val mine = all.filter { it.memberId == memberId }
        if (mine.isEmpty()) return

        val seenIds = settingsStore.getNotifiedNotificationIds(householdId)
        if (seenIds.isEmpty()) {
            // Primer sondeo de este hogar (o el primero con datos reales):
            // fija la base sin notificar — evita "inundar" con el histórico
            // completo justo tras instalar/loguearse/unirse a un hogar nuevo.
            settingsStore.setNotifiedNotificationIds(householdId, mine.map { it.id }.toSet())
            return
        }

        // Excluye también las que ya se marcaron leídas en la app (p.ej. el
        // usuario abrió NotificationListScreen antes de que corriera este
        // sondeo) — sin esto, algo ya gestionado en la UI podía disparar
        // igualmente la notificación local del sistema (panel de
        // notificaciones 2026-09-05, QA).
        val newOnes = mine.filter { it.id !in seenIds && !it.read }.sortedBy { it.createdAt }
        if (newOnes.isEmpty()) {
            settingsStore.setNotifiedNotificationIds(householdId, mine.map { it.id }.toSet())
            return
        }

        newOnes.forEach { n ->
            NotificationHelper.showUpdateNotification(
                context = applicationContext,
                notificationDocId = n.id,
                // Idioma del LECTOR (este dispositivo, `lang` ya resuelto
                // arriba), no el de quien la escribió — ver NotificationText
                // KDoc (panel de notificaciones 2026-09-05, IMPORTANTE).
                title = NotificationText.title(n, lang),
                message = NotificationText.message(n, lang),
                householdId = householdId,
                taskId = n.taskId,
                lang = lang
            )
        }

        settingsStore.setNotifiedNotificationIds(householdId, mine.map { it.id }.toSet())
    }
}
