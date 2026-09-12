package com.gym.service

import com.gym.domain.ExpiryWebhookPayload
import com.gym.domain.Subscription
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

interface WebhookService {
    suspend fun sendExpiryWarning(subscription: Subscription, daysRemaining: Int): Boolean
}

class KtorWebhookService(
    private val webhookEndpointUrl: String = "https://webhook.site/gym-expiry-alerts",
    private val enabled: Boolean = false,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }
    }
) : WebhookService {

    private val logger = LoggerFactory.getLogger(KtorWebhookService::class.java)

    override suspend fun sendExpiryWarning(subscription: Subscription, daysRemaining: Int): Boolean {
        val payload = ExpiryWebhookPayload(
            event = "SUBSCRIPTION_EXPIRING",
            memberId = subscription.memberId,
            subscriptionId = subscription.id,
            daysRemaining = daysRemaining,
            expirationDate = subscription.endDate.toString(),
            autoRenew = subscription.autoRenew
        )

        if (!enabled) {
            logger.info("Webhook disabled by config. Simulated expiry warning for sub: ${subscription.id} (Member: ${subscription.memberId}, Days left: $daysRemaining)")
            return true
        }

        return try {
            logger.info("Dispatching 7-day expiry webhook for sub: ${subscription.id} to $webhookEndpointUrl")
            val response = client.post(webhookEndpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Failed to send expiration webhook for subscription ${subscription.id} to $webhookEndpointUrl", e)
            false
        }
    }
}

