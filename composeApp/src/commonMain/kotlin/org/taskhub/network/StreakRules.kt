package org.taskhub.network

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Cálculo puro de racha (días consecutivos completando alguna tarea),
 * extraído de `TaskScreenModel.updateMemberStreak` (R19, 2026-09-24) para
 * poder testearlo sin red/Koin. La escritura a Firestore
 * ([org.taskhub.network.HouseholdRepository.updateMemberStreak]) sigue en el
 * screen model — este objeto solo decide los nuevos valores.
 */
object StreakRules {

    /** Nuevo estado de racha a persistir. */
    data class StreakUpdate(
        val currentStreak: Int,
        val bestStreak: Int,
        val lastStreakDate: Long
    )

    /**
     * - Si [lastStreakDateEpoch] ya es de [today] → `null` (ya se contó hoy,
     *   nada que escribir).
     * - Si la última racha fue ayer → +1 a [currentStreak].
     * - Si hay un hueco (o es la primera vez, `lastStreakDateEpoch == 0`) →
     *   racha nueva de 1.
     *
     * @param lastStreakDateEpoch epoch millis de inicio del día de la última
     *   racha contada (0 = nunca). [org.taskhub.network.models.MemberResponse.lastStreakDate].
     * @param tz zona horaria para convertir [lastStreakDateEpoch] y calcular
     *   el epoch de inicio de "hoy".
     */
    fun nextStreak(
        lastStreakDateEpoch: Long,
        currentStreak: Int,
        bestStreak: Int,
        today: LocalDate,
        tz: TimeZone
    ): StreakUpdate? {
        val todayEpoch = today.atStartOfDayIn(tz).toEpochMilliseconds()
        if (lastStreakDateEpoch >= todayEpoch) return null

        val newStreak = if (lastStreakDateEpoch == 0L) {
            1
        } else {
            val lastDate = Instant.fromEpochMilliseconds(lastStreakDateEpoch).toLocalDateTime(tz).date
            val yesterday = today.plus(-1, DateTimeUnit.DAY)
            if (lastDate == yesterday) currentStreak + 1 else 1
        }
        val newBest = maxOf(newStreak, bestStreak)
        return StreakUpdate(currentStreak = newStreak, bestStreak = newBest, lastStreakDate = todayEpoch)
    }
}
