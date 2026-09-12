package com.gym.jobs

import com.gym.domain.SubscriptionStatus
import com.gym.repository.SubscriptionRepository
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class AutoUnfreezeJob(
    private val subscriptionRepository: SubscriptionRepository,
    private val checkInterval: Duration = 6.hours
) {
    private val logger = LoggerFactory.getLogger(AutoUnfreezeJob::class.java)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.Default) {
            logger.info("Starting AutoUnfreezeJob background worker...")
            while (isActive) {
                try {
                    val count = processExpiredFreezes()
                    if (count > 0) {
                        logger.info("AutoUnfreezeJob completed: reactivated $count memberships.")
                    }
                } catch (e: Exception) {
                    logger.error("Error executing AutoUnfreezeJob cycle", e)
                }
                delay(checkInterval)
            }
        }
    }

    suspend fun processExpiredFreezes(): Int {
        val now = Clock.System.now()
        val expiredFreezes = subscriptionRepository.findExpiredFreezes(now)
        var count = 0

        for (freeze in expiredFreezes) {
            try {
                subscriptionRepository.completeFreeze(freeze.id, now)
                subscriptionRepository.updateStatus(freeze.subscriptionId, SubscriptionStatus.ACTIVE)
                logger.info("Auto-unfroze subscription ${freeze.subscriptionId} for member ${freeze.memberId}")
                count++
            } catch (e: Exception) {
                logger.error("Failed to auto-unfreeze subscription ${freeze.subscriptionId}", e)
            }
        }
        return count
    }

    fun stop() {
        job?.cancel()
    }
}

