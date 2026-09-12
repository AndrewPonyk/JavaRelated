package com.gym.plugins

import com.gym.jobs.AutoUnfreezeJob
import com.gym.jobs.ExpiryWarningJob
import com.gym.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext

@Serializable
data class HealthStatus(
    val status: String,
    val service: String,
    val version: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun Application.configureRouting() {
    val expiryWarningJob = GlobalContext.get().get<ExpiryWarningJob>()
    val autoUnfreezeJob = GlobalContext.get().get<AutoUnfreezeJob>()

    // Start background coroutine workers
    expiryWarningJob.start(this)
    autoUnfreezeJob.start(this)

    routing {
        get("/") {
            val htmlStream = this::class.java.classLoader.getResourceAsStream("static/index.html")
            if (htmlStream != null) {
                val html = htmlStream.bufferedReader().use { it.readText() }
                call.respondText(html, ContentType.Text.Html)
            } else {
                call.respondText("Gym Membership API is running!", ContentType.Text.Plain)
            }
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                HealthStatus(
                    status = "UP",
                    service = "gym-membership-api",
                    version = "0.0.1"
                )
            )
        }

        authRoutes()
        memberRoutes()
        subscriptionRoutes()
        checkInRoutes()
    }
}
