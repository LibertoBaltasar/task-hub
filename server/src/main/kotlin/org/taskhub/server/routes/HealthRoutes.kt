package org.taskhub.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRouting() {
    get("/health") {
        call.respond(
            status = HttpStatusCode.OK,
            message = HealthResponse(status = "ok")
        )
    }
}
