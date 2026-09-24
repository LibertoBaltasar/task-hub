package org.taskhub.network

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [StreakRules.nextStreak] decide si completar una tarea hoy cuenta como
 * racha nueva, consecutiva, o ya contada — sin tocar red/Firestore.
 */
class StreakRulesTest {

    private val tz = TimeZone.currentSystemDefault()
    private val today = LocalDate(2026, 9, 24)
    private val yesterday = today.plus(-1, DateTimeUnit.DAY)
    private val twoDaysAgo = today.plus(-2, DateTimeUnit.DAY)

    /** Primera vez que se completa algo (nunca hubo racha): empieza en 1. */
    @Test
    fun nextStreak_firstEver_startsAtOne() {
        val update = StreakRules.nextStreak(
            lastStreakDateEpoch = 0L,
            currentStreak = 0,
            bestStreak = 0,
            today = today,
            tz = tz
        )
        assertEquals(1, update?.currentStreak)
        assertEquals(1, update?.bestStreak)
    }

    /** Última racha fue ayer: la racha actual se incrementa en 1. */
    @Test
    fun nextStreak_consecutiveDay_incrementsStreak() {
        val update = StreakRules.nextStreak(
            lastStreakDateEpoch = yesterday.atStartOfDayIn(tz).toEpochMilliseconds(),
            currentStreak = 4,
            bestStreak = 4,
            today = today,
            tz = tz
        )
        assertEquals(5, update?.currentStreak)
        assertEquals(5, update?.bestStreak)
    }

    /** Hueco de más de un día: la racha se reinicia a 1, pero bestStreak no baja. */
    @Test
    fun nextStreak_gap_resetsToOneButKeepsBest() {
        val update = StreakRules.nextStreak(
            lastStreakDateEpoch = twoDaysAgo.atStartOfDayIn(tz).toEpochMilliseconds(),
            currentStreak = 4,
            bestStreak = 10,
            today = today,
            tz = tz
        )
        assertEquals(1, update?.currentStreak)
        assertEquals(10, update?.bestStreak)
    }

    /** Ya se contó hoy: no hay nada que actualizar. */
    @Test
    fun nextStreak_alreadyCountedToday_returnsNull() {
        val update = StreakRules.nextStreak(
            lastStreakDateEpoch = today.atStartOfDayIn(tz).toEpochMilliseconds(),
            currentStreak = 4,
            bestStreak = 4,
            today = today,
            tz = tz
        )
        assertNull(update)
    }

    /** Nueva racha que supera el mejor histórico: bestStreak sube con ella. */
    @Test
    fun nextStreak_newRecord_updatesBest() {
        val update = StreakRules.nextStreak(
            lastStreakDateEpoch = yesterday.atStartOfDayIn(tz).toEpochMilliseconds(),
            currentStreak = 9,
            bestStreak = 9,
            today = today,
            tz = tz
        )
        assertEquals(10, update?.currentStreak)
        assertEquals(10, update?.bestStreak)
    }
}
