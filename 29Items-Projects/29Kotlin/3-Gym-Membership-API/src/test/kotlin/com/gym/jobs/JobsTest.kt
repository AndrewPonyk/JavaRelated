package com.gym.jobs

import com.gym.domain.*
import com.gym.repository.*
import com.gym.service.KtorWebhookService
import com.gym.service.WebhookService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class JobsTest {

    private lateinit var subRepo: ExposedSubscriptionRepository
    private lateinit var memberRepo: ExposedMemberRepository

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:gymdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "org.h2.Driver", "sa", "")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                MembersTable,
                PlansTable,
                SubscriptionsTable,
                FreezeRecordsTable,
                CheckInsTable
            )
        }
        subRepo = ExposedSubscriptionRepository()
        memberRepo = ExposedMemberRepository()
    }

    @Test
    fun testExpiryWarningJobFindsAndProcessesExpiringSubscriptions() = runBlocking {
        val now = Clock.System.now()
        val plan = subRepo.createPlan(
            Plan("plan-test-job-" + System.currentTimeMillis(), "Job Plan", MembershipTier.BASIC, 1999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberRepo.create(
            Member("m-exp-" + System.currentTimeMillis(), "exp" + System.currentTimeMillis() + "@test.com", "Exp User", "BADGE-EXP-" + System.currentTimeMillis(), Role.ROLE_MEMBER, now, now),
            "hash"
        )

        // Subscription expiring in 7 days and 12 hours from now (between 7 and 8 days)
        val subId = "sub-exp-" + System.currentTimeMillis()
        val subExpiring = subRepo.createSubscription(
            Subscription(
                id = subId,
                memberId = member.id,
                planId = plan.id,
                status = SubscriptionStatus.ACTIVE,
                startDate = now - 23.days,
                endDate = now + 7.days + 12.hours,
                remainingGuestPasses = 0,
                autoRenew = true
            )
        )

        var webhookCalled = false
        val mockWebhook = object : WebhookService {
            override suspend fun sendExpiryWarning(subscription: Subscription, daysRemaining: Int): Boolean {
                if (subscription.id == subId) {
                    webhookCalled = true
                }
                return true
            }
        }

        val job = ExpiryWarningJob(subRepo, mockWebhook)
        val count = job.processExpiringSubscriptions()
        assertTrue(count >= 1)
        assertTrue(webhookCalled)
    }

    @Test
    fun testAutoUnfreezeJobReactivatesExpiredFreezes() = runBlocking {
        val now = Clock.System.now()
        val plan = subRepo.createPlan(
            Plan("plan-unfreeze-job-" + System.currentTimeMillis(), "Unfreeze Plan", MembershipTier.BASIC, 1999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberRepo.create(
            Member("m-unfreeze-" + System.currentTimeMillis(), "unfreeze" + System.currentTimeMillis() + "@test.com", "Unfreeze User", "BADGE-UF-" + System.currentTimeMillis(), Role.ROLE_MEMBER, now, now),
            "hash"
        )

        val subId = "sub-frozen-" + System.currentTimeMillis()
        val sub = subRepo.createSubscription(
            Subscription(
                id = subId,
                memberId = member.id,
                planId = plan.id,
                status = SubscriptionStatus.FROZEN,
                startDate = now - 30.days,
                endDate = now + 30.days,
                remainingGuestPasses = 0
            )
        )

        val freezeId = "frz-" + System.currentTimeMillis()
        subRepo.createFreezeRecord(
            FreezeRecord(
                id = freezeId,
                subscriptionId = sub.id,
                memberId = member.id,
                startDate = now - 15.days,
                scheduledEndDate = now - 1.days,
                actualEndDate = null,
                daysFrozen = 14
            )
        )

        val autoUnfreezeJob = AutoUnfreezeJob(subRepo)
        val reactivated = autoUnfreezeJob.processExpiredFreezes()
        assertTrue(reactivated >= 1)

        val updatedSub = subRepo.findById(sub.id)
        assertNotNull(updatedSub)
        assertEquals(SubscriptionStatus.ACTIVE, updatedSub.status)

        val activeFreeze = subRepo.getActiveFreeze(sub.id)
        assertNull(activeFreeze)
    }

    @Test
    fun testWebhookServiceSimulatedWhenDisabled() = runBlocking {
        val now = Clock.System.now()
        val webhookService = KtorWebhookService(enabled = false)
        val sub = Subscription(
            id = "sub-sim-01",
            memberId = "mem-sim-01",
            planId = "plan-01",
            status = SubscriptionStatus.ACTIVE,
            startDate = now,
            endDate = now + 7.days,
            remainingGuestPasses = 0
        )
        val sent = webhookService.sendExpiryWarning(sub, 7)
        assertTrue(sent)
    }
}
