package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Google Calendar REST API integration.
 *
 * Creates calendar events for tasks — so they show up on the user's Google Calendar.
 * Uses the OAuth access token obtained via Google Sign-In linking.
 *
 * Google Calendar API v3 docs:
 *   https://developers.google.com/calendar/api/v3/reference/events/insert
 */
class GoogleCalendarRepository(
    private val apiKey: String = DEFAULT_API_KEY
) {
    private val calendarBaseUrl = "https://www.googleapis.com/calendar/v3"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    /**
     * Finds a calendar owned/subscribed by the user whose `summary` (display
     * name) matches, or creates one if none exists. Idempotent — safe to call
     * every time a task needs to be synced.
     *
     * @param accessToken OAuth Bearer access token (Calendar scope).
     * @param summary Calendar display name (e.g. "Task Hub").
     * @return the `calendarId` to use with [createEvent]/[updateEvent]/[deleteEvent].
     */
    suspend fun ensureCalendar(accessToken: String, summary: String): String {
        val existingId = findCalendarIdByName(accessToken, summary)
        if (existingId != null) return existingId

        val response: CalendarInsertResponse = client.post("$calendarBaseUrl/calendars") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(CalendarInsertRequest(summary = summary))
        }.body()

        return response.id
    }

    private suspend fun findCalendarIdByName(accessToken: String, summary: String): String? {
        val response: CalendarListResponse = client.get("$calendarBaseUrl/users/me/calendarList") {
            header("Authorization", "Bearer $accessToken")
            parameter("fields", "items(id,summary)")
        }.body()

        return response.items?.firstOrNull { it.summary == summary }?.id
    }

    /**
     * Creates an event on the given calendar.
     *
     * @param accessToken OAuth Bearer access token (Calendar scope).
     * @param calendarId Target calendar, e.g. from [ensureCalendar] (or "primary").
     * @param summary Event title.
     * @param description Event description (task notes).
     * @param dueDateEpochMs Deadline epoch millis. Used as the event date for
     *                       "once" tasks. For recurring tasks, today's date is used.
     * @return the created Google Calendar event, or throws on error.
     */
    suspend fun createEvent(
        accessToken: String,
        calendarId: String = "primary",
        summary: String,
        description: String,
        dueDateEpochMs: Long
    ): CalendarEventResponse {
        val response: CalendarEventResponse = client.post(
            "$calendarBaseUrl/calendars/$calendarId/events"
        ) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(buildEventRequest(summary, description, dueDateEpochMs))
        }.body()

        return response
    }

    /**
     * Updates an existing event (e.g. its title or due date changed).
     *
     * @param calendarId Calendar the event lives in.
     * @param eventId Event to update, as returned by [createEvent].
     */
    suspend fun updateEvent(
        accessToken: String,
        calendarId: String,
        eventId: String,
        summary: String,
        description: String,
        dueDateEpochMs: Long
    ): CalendarEventResponse {
        val response: CalendarEventResponse = client.put(
            "$calendarBaseUrl/calendars/$calendarId/events/$eventId"
        ) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(buildEventRequest(summary, description, dueDateEpochMs))
        }.body()

        return response
    }

    /** Deletes an event, e.g. when the task is deleted or unlinked from Calendar. */
    suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String) {
        client.delete("$calendarBaseUrl/calendars/$calendarId/events/$eventId") {
            header("Authorization", "Bearer $accessToken")
        }
    }

    /**
     * Checks whether the given access token is valid by making a lightweight
     * request to the Calendar API.
     */
    suspend fun validateToken(accessToken: String): Boolean {
        return try {
            client.get("$calendarBaseUrl/calendars/primary") {
                header("Authorization", "Bearer $accessToken")
                parameter("fields", "id")
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun buildEventRequest(
        summary: String,
        description: String,
        dueDateEpochMs: Long
    ): CalendarEventRequest {
        // Determine event date string (YYYY-MM-DD)
        val dateString = if (dueDateEpochMs > 0) {
            epochMillisToDateString(dueDateEpochMs)
        } else {
            // No due date — use today
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            epochMillisToDateString(now)
        }

        return CalendarEventRequest(
            summary = summary,
            description = description,
            start = CalendarEventDateTime(date = dateString),
            end = CalendarEventDateTime(date = dateString)
        )
    }

    private fun epochMillisToDateString(epochMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = local.monthNumber.toString().padStart(2, '0')
        val day = local.dayOfMonth.toString().padStart(2, '0')
        return "${local.year}-$month-$day"
    }

    companion object {
        private const val DEFAULT_API_KEY = "AIzaSyCqD2r21Y8AXEYR2Dw3v3QpN5hA6CULNcs"
    }
}

// ── Google Calendar API DTOs ─────────────────────────────

@Serializable
data class CalendarEventRequest(
    val summary: String,
    val description: String,
    val start: CalendarEventDateTime,
    val end: CalendarEventDateTime
)

@Serializable
data class CalendarEventDateTime(
    val date: String
)

@Serializable
data class CalendarEventResponse(
    val id: String,
    val htmlLink: String? = null,
    val status: String? = null
)

@Serializable
data class CalendarListResponse(
    val items: List<CalendarListItem>? = null
)

@Serializable
data class CalendarListItem(
    val id: String,
    val summary: String? = null
)

@Serializable
data class CalendarInsertRequest(
    val summary: String
)

@Serializable
data class CalendarInsertResponse(
    val id: String,
    val summary: String? = null
)