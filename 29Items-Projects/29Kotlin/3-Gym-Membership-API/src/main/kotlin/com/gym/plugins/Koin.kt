package com.gym.plugins

import com.gym.di.createAppModule
import io.ktor.server.application.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            slf4jLogger()
            modules(createAppModule(environment.config))
        }
    }
}

