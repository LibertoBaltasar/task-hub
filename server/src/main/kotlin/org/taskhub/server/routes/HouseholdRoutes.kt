package org.taskhub.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.taskhub.server.models.CreateHouseholdRequest
import org.taskhub.server.models.ErrorResponse
import org.taskhub.server.models.JoinHouseholdRequest
import org.taskhub.server.services.HouseholdService

fun Route.householdRouting(service: HouseholdService) {
    route("/api/households") {

        post {
            val request = call.receive<CreateHouseholdRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("name is required"))
                return@post
            }
            val household = service.create(request.name)
            call.respond(HttpStatusCode.Created, household)
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("id is required"))
                return@get
            }
            val household = service.getById(id)
            if (household != null) {
                call.respond(HttpStatusCode.OK, household)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("household not found"))
            }
        }

        post("/join") {
            val request = call.receive<JoinHouseholdRequest>()
            if (request.inviteCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("inviteCode is required"))
                return@post
            }
            val household = service.join(request.inviteCode)
            if (household != null) {
                call.respond(HttpStatusCode.OK, household)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("invalid invite code"))
            }
        }
    }
}