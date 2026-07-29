package org.taskhub.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.taskhub.server.routes.healthRouting
import org.taskhub.server.routes.householdRouting
import org.taskhub.server.routes.memberRouting
import org.taskhub.server.services.HouseholdService
import org.taskhub.server.services.MemberService

fun Application.configureRouting() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: ${cause.message ?: "Internal Server Error"}",
                contentType = ContentType.Text.Plain
            )
        }
    }

    val householdService = HouseholdService()
    val memberService = MemberService()

    routing {
        healthRouting()
        householdRouting(householdService)
        memberRouting(memberService, householdService)
    }
}