package com.gym

import com.gym.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureDatabase()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureStatusPages()
    configureRouting()
}
