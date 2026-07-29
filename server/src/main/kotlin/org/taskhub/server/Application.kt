package org.taskhub.server

import io.ktor.server.application.*
import org.taskhub.server.plugins.configureRouting
import org.taskhub.server.plugins.configureSerialization

fun main() {
    io.ktor.server.netty.EngineMain.main(arrayOf())
}

fun Application.module() {
    configureSerialization()
    configureRouting()
}
