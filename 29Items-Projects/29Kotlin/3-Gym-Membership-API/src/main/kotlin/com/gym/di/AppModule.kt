package com.gym.di

import com.gym.jobs.AutoUnfreezeJob
import com.gym.jobs.ExpiryWarningJob
import com.gym.repository.*
import com.gym.security.AntiPassbackLimiter
import com.gym.security.InMemoryAntiPassbackLimiter
import com.gym.security.JwtConfig
import com.gym.service.*
import io.ktor.server.config.*
import org.koin.core.module.Module
import org.koin.dsl.module

fun createAppModule(config: ApplicationConfig? = null): List<Module> {
    val jwtSecret = config?.propertyOrNull("jwt.secret")?.getString()
        ?: "gym-api-super-secret-key-32-chars-minimum-for-production"
    val jwtIssuer = config?.propertyOrNull("jwt.issuer")?.getString()
        ?: "https://api.gymmembership.com/"
    val jwtAudience = config?.propertyOrNull("jwt.audience")?.getString()
        ?: "gym-api-consumers"
    val jwtExpHours = config?.propertyOrNull("jwt.expirationHours")?.getString()?.toLongOrNull() ?: 24L

    val maxCapacity = config?.propertyOrNull("gym.maxCapacity")?.getString()?.toIntOrNull() ?: 250
    val cooldownMinutes = config?.propertyOrNull("gym.antiPassbackCooldownMinutes")?.getString()?.toLongOrNull() ?: 15L

    val webhookUrl = config?.propertyOrNull("webhook.endpointUrl")?.getString() ?: "https://webhook.site/gym-expiry-alerts"
    val webhookEnabled = config?.propertyOrNull("webhook.enabled")?.getString()?.toBooleanStrictOrNull() ?: false

    val databaseModule = module {
        single<MemberRepository> { ExposedMemberRepository() }
        single<SubscriptionRepository> { ExposedSubscriptionRepository() }
        single<CheckInRepository> { ExposedCheckInRepository() }
    }

    val serviceModule = module {
        single<JwtConfig> {
            JwtConfig(
                secret = jwtSecret,
                issuer = jwtIssuer,
                audience = jwtAudience,
                validityMs = jwtExpHours * 60 * 60 * 1000L
            )
        }
        single<AntiPassbackLimiter> { InMemoryAntiPassbackLimiter(cooldownMinutes = cooldownMinutes) }
        single<CapacityService> { InMemoryCapacityService(maxLimit = maxCapacity) }
        single<WebhookService> { KtorWebhookService(webhookEndpointUrl = webhookUrl, enabled = webhookEnabled) }

        single { MemberService(get<MemberRepository>()) }
        single { SubscriptionService(get<SubscriptionRepository>(), get<MemberRepository>()) }
        single { CheckInService(get<MemberRepository>(), get<SubscriptionService>(), get<CheckInRepository>(), get<CapacityService>(), get<AntiPassbackLimiter>()) }
    }

    val jobsModule = module {
        single { ExpiryWarningJob(get<SubscriptionRepository>(), get<WebhookService>()) }
        single { AutoUnfreezeJob(get<SubscriptionRepository>()) }
    }

    return listOf(databaseModule, serviceModule, jobsModule)

}

val appModule = createAppModule()

