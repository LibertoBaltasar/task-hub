package org.taskhub.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.taskhub.server.models.CreateMemberRequest
import org.taskhub.server.models.ErrorResponse
import org.taskhub.server.services.HouseholdService
import org.taskhub.server.services.MemberService

fun Route.memberRouting(memberService: MemberService, householdService: HouseholdService) {
    route("/api/households/{id}/members") {

        get {
            val householdId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("household id is required"))
                return@get
            }
            if (!householdService.exists(householdId)) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("household not found"))
                return@get
            }
            val members = memberService.listByHousehold(householdId)
            call.respond(HttpStatusCode.OK, members)
        }

        post {
            val householdId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("household id is required"))
                return@post
            }
            if (!householdService.exists(householdId)) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("household not found"))
                return@post
            }
            val request = call.receive<CreateMemberRequest>()
            if (request.displayName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("displayName is required"))
                return@post
            }
            if (request.role !in listOf("admin", "child")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("role must be 'admin' or 'child'"))
                return@post
            }
            val member = memberService.create(
                householdId = householdId,
                displayName = request.displayName,
                role = request.role,
                avatarUrl = request.avatarUrl
            )
            call.respond(HttpStatusCode.Created, member)
        }

        delete("/{memberId}") {
            val householdId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("household id is required"))
                return@delete
            }
            val memberId = call.parameters["memberId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("member id is required"))
                return@delete
            }
            if (!memberService.exists(householdId, memberId)) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("member not found"))
                return@delete
            }
            val left = memberService.leave(householdId, memberId)
            if (left) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("could not remove member"))
            }
        }
    }
}