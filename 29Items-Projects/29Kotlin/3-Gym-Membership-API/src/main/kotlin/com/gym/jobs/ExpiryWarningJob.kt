package com.gym.jobs

import com.gym.repository.SubscriptionRepository
import com.gym.service.WebhookService
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ExpiryWarningJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val webhookService: WebhookService
) {
    private val logger = LoggerFactory.getLogger(ExpiryWarningJob::class.java)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.Default) {
            logger.info("Starting ExpiryWarningJob background worker...")
            while (isActive) {
                try {
                    processExpiringSubscriptions()
                } catch (e: Exception) {
                    logger.error("Error executing ExpiryWarningJob cycle", e)
                }
                delay(24.hours) // Run every 24 hours
            }
        }
    }

    suspend fun processExpiringSubscriptions(): Int {
        val now = Clock.System.now()
        val in7Days = now.plus(7.days)
        val in8Days = now.plus(8.days)

        // Find subscriptions expiring between 7 and 8 days from now that haven't been warned
        val expiring = subscriptionRepository.findSubscriptionsExpiringBetween(in7Days, in8Days)
        logger.info("Found ${expiring.size} subscriptions expiring in ~7 days")
        var count = 0

        for (sub in expiring) {
            val sent = webhookService.sendExpiryWarning(sub, daysRemaining = 7)
            if (sent) {
                subscriptionRepository.markWarningSent(sub.id, now)
                logger.info("Successfully marked warning sent for subscription ${sub.id}")
                count++
            }
        }
        return count
    }

    fun stop() {
        job?.cancel()
    }
}

