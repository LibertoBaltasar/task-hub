package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
     * Creates an event on the user's primary calendar.
     *
     * @param accessToken OAuth Bearer token from Google Sign-In (idToken works
     *                    when the calendar scope is included in the sign-in request).
     * @param summary Event title.
     * @param description Event description (task notes).
     * @param dueDateEpochMs Deadline epoch millis. Used as the event date for
     *                       "once" tasks. For recurring tasks, today's date is used.
     * @return link to the created Google Calendar event, or throws on error.
     */
    suspend fun createEvent(
        accessToken: String,
        summary: String,
        description: String,
        dueDateEpochMs: Long
    ): CalendarEventResponse {
        // Determine event date string (YYYY-MM-DD)
        val dateString = if (dueDateEpochMs > 0) {
            epochMillisToDateString(dueDateEpochMs)
        } else {
            // No due date — use today
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            epochMillisToDateString(now)
        }

        val eventBody = CalendarEventRequest(
            summary = summary,
            description = description,
            start = CalendarEventDateTime(date = dateString),
            end = CalendarEventDateTime(date = dateString)
        )

        val response: CalendarEventResponse = client.post(
            "$calendarBaseUrl/calendars/primary/events"
        ) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(eventBody)
        }.body()

        return response
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
        } catch (_: Exception) {
            false
        }
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