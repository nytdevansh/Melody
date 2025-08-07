package com.melody.backend

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.melody.backend.plugins.*
import com.melody.backend.database.DatabaseFactory

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toInt() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    // Configure plugins
    configureSerialization()
    configureCORS()
    configureLogging()
    configureStatusPages()
    configureRouting()
}