package com.melody.backend.plugins

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import com.melody.backend.routes.musicRoutes

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
        allowCredentials = true
        anyHost() // Don't do this in production! Specify your actual domains
    }
}

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val uri = call.request.uri
            val userAgent = call.request.headers["User-Agent"]
            "$status: $httpMethod $uri - $userAgent"
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "success" to false,
                    "error" to "Internal server error",
                    "message" to (cause.message ?: "Unknown error")
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                mapOf(
                    "success" to false,
                    "error" to "Not found",
                    "message" to "The requested resource was not found"
                )
            )
        }

        status(HttpStatusCode.BadRequest) { call, status ->
            call.respond(
                status,
                mapOf(
                    "success" to false,
                    "error" to "Bad request",
                    "message" to "The request was invalid"
                )
            )
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(
                mapOf(
                    "service" to "Melody Music Streaming API",
                    "version" to "1.0.0",
                    "status" to "running"
                )
            )
        }

        get("/health") {
            call.respond(
                mapOf(
                    "status" to "healthy",
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }

        // Music API routes
        musicRoutes()
    }
}