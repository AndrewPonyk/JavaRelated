package com.gym.service

import com.gym.domain.*
import com.gym.repository.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class SubscriptionServiceTest {

    private lateinit var memberService: MemberService
    private lateinit var subscriptionService: SubscriptionService
    private val subRepo = ExposedSubscriptionRepository()
    private val memberRepo = ExposedMemberRepository()

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
        memberService = MemberService(memberRepo)
        subscriptionService = SubscriptionService(subRepo, memberRepo)
    }

    @Test
    fun testTierFeatureGatingRules() {
        val basicPlan = Plan(
            id = "test-basic",
            name = "Basic",
            tier = MembershipTier.BASIC,
            priceCents = 2999,
            durationDays = 30,
            maxGuestPasses = 0,
            features = listOf(GymFeature.GYM_FLOOR, GymFeature.LOCKER_ROOM)
        )

        val premiumPlan = Plan(
            id = "test-premium",
            name = "Premium",
            tier = MembershipTier.PREMIUM,
            priceCents = 5999,
            durationDays = 30,
            maxGuestPasses = 2,
            features = listOf(GymFeature.GYM_FLOOR, GymFeature.LOCKER_ROOM, GymFeature.POOL, GymFeature.SAUNA, GymFeature.GROUP_CLASSES)
        )

        val vipPlan = Plan(
            id = "test-vip",
            name = "VIP",
            tier = MembershipTier.VIP,
            priceCents = 59999,
            durationDays = 365,
            maxGuestPasses = 5,
            features = listOf(
                GymFeature.GYM_FLOOR, GymFeature.LOCKER_ROOM, GymFeature.POOL,
                GymFeature.SAUNA, GymFeature.GROUP_CLASSES, GymFeature.VIP_LOUNGE,
                GymFeature.PERSONAL_TRAINER_CONSULT, GymFeature.FREE_TOWEL_SERVICE
            )
        )

        // Basic should succeed for GYM_FLOOR but fail for POOL, SAUNA, VIP_LOUNGE, CLASS_STUDIO
        subscriptionService.validateFeatureGating(basicPlan, GymZone.GYM_FLOOR)
        assertFailsWith<FeatureGatedException> {
            subscriptionService.validateFeatureGating(basicPlan, GymZone.POOL)
        }
        assertFailsWith<FeatureGatedException> {
            subscriptionService.validateFeatureGating(basicPlan, GymZone.SAUNA)
        }
        assertFailsWith<FeatureGatedException> {
            subscriptionService.validateFeatureGating(basicPlan, GymZone.VIP_LOUNGE)
        }
        assertFailsWith<FeatureGatedException> {
            subscriptionService.validateFeatureGating(basicPlan, GymZone.CLASS_STUDIO)
        }

        // Premium should succeed for GYM_FLOOR, POOL, SAUNA, CLASS_STUDIO, but fail for VIP_LOUNGE
        subscriptionService.validateFeatureGating(premiumPlan, GymZone.GYM_FLOOR)
        subscriptionService.validateFeatureGating(premiumPlan, GymZone.POOL)
        subscriptionService.validateFeatureGating(premiumPlan, GymZone.SAUNA)
        subscriptionService.validateFeatureGating(premiumPlan, GymZone.CLASS_STUDIO)
        assertFailsWith<FeatureGatedException> {
            subscriptionService.validateFeatureGating(premiumPlan, GymZone.VIP_LOUNGE)
        }

        // VIP should succeed for all zones
        subscriptionService.validateFeatureGating(vipPlan, GymZone.GYM_FLOOR)
        subscriptionService.validateFeatureGating(vipPlan, GymZone.POOL)
        subscriptionService.validateFeatureGating(vipPlan, GymZone.SAUNA)
        subscriptionService.validateFeatureGating(vipPlan, GymZone.CLASS_STUDIO)
        subscriptionService.validateFeatureGating(vipPlan, GymZone.VIP_LOUNGE)
    }

    @Test
    fun testCreateSubscriptionValidation() = runBlocking {
        val planId = "plan-val-" + System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan(planId, "Val Plan", MembershipTier.BASIC, 2999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberService.register(
            RegisterMemberRequest("sub.val." + System.currentTimeMillis() + "@test.com", "Password123!", "Sub Val", "BADGE-SV-" + System.currentTimeMillis())
        )

        // Invalid member ID -> MemberNotFoundException
        assertFailsWith<MemberNotFoundException> {
            subscriptionService.createSubscription(CreateSubscriptionRequest("non-existent-member", plan.id))
        }

        // Invalid plan ID -> PlanNotFoundException
        assertFailsWith<PlanNotFoundException> {
            subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, "non-existent-plan"))
        }

        // Valid creation
        val sub = subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)

        // Duplicate creation attempt -> DuplicateSubscriptionException
        assertFailsWith<DuplicateSubscriptionException> {
            subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))
        }
    }

    @Test
    fun testFreezeMembershipWithin30DayAnnualQuota() = runBlocking {
        val planId = "plan-annual-" + System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan(planId, "VIP Plan", MembershipTier.VIP, 59999, 365, 5, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberService.register(
            RegisterMemberRequest("freeze.user." + System.currentTimeMillis() + "@test.com", "Pass1234!", "Freeze User", "BADGE-FRZ-" + System.currentTimeMillis())
        )

        val sub = subscriptionService.createSubscription(
            CreateSubscriptionRequest(member.id, plan.id)
        )
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)

        // Invalid freeze days -> ValidationException
        assertFailsWith<ValidationException> {
            subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(0))
        }
        assertFailsWith<ValidationException> {
            subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(35))
        }

        // First freeze for 14 days -> should succeed
        val freeze1 = subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(14, "Vacation"))
        assertEquals(14, freeze1.daysFrozen)

        // Cannot freeze while already frozen
        assertFailsWith<FreezeLimitExceededException> {
            subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(5))
        }

        // Unfreeze
        val activeSub = subscriptionService.unfreezeMembership(member.id)
        assertEquals(SubscriptionStatus.ACTIVE, activeSub.status)

        // Second freeze for 10 days -> total 24 <= 30 -> should succeed
        val freeze2 = subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(10, "Injury"))
        assertEquals(10, freeze2.daysFrozen)
        subscriptionService.unfreezeMembership(member.id)

        // Third freeze for 10 days -> total 24 + 10 = 34 > 30 -> should fail with FreezeLimitExceededException
        assertFailsWith<FreezeLimitExceededException> {
            subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(10, "Exceeding limit"))
        }
    }

    @Test
    fun testUnfreezeWhenNotFrozenThrows() = runBlocking {
        val planId = "plan-uf-" + System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan(planId, "Basic Plan", MembershipTier.BASIC, 2999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberService.register(
            RegisterMemberRequest("unfreeze.err." + System.currentTimeMillis() + "@test.com", "Pass1234!", "Unfreeze User", "BADGE-UF-ERR-" + System.currentTimeMillis())
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))

        assertFailsWith<IllegalArgumentException> {
            subscriptionService.unfreezeMembership(member.id)
        }
    }

    @Test
    fun testUseGuestPassSuccessAndExhaustion() = runBlocking {
        val planId = "plan-gp-" + System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan(planId, "Premium Plan", MembershipTier.PREMIUM, 5999, 30, 2, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberService.register(
            RegisterMemberRequest("gp.user." + System.currentTimeMillis() + "@test.com", "Pass1234!", "GP User", "BADGE-GP-" + System.currentTimeMillis())
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))

        val remaining1 = subscriptionService.useGuestPass(member.id)
        assertEquals(1, remaining1)

        val remaining2 = subscriptionService.useGuestPass(member.id)
        assertEquals(0, remaining2)

        // Third attempt must throw InsufficientGuestPassesException
        assertFailsWith<InsufficientGuestPassesException> {
            subscriptionService.useGuestPass(member.id)
        }
    }

    @Test
    fun testUseGuestPassWhenFrozenThrows() = runBlocking {
        val planId = "plan-gp-frz-" + System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan(planId, "Premium Plan", MembershipTier.PREMIUM, 5999, 30, 2, listOf(GymFeature.GYM_FLOOR))
        )
        val member = memberService.register(
            RegisterMemberRequest("gp.frz." + System.currentTimeMillis() + "@test.com", "Pass1234!", "GP Frz", "BADGE-GP-FRZ-" + System.currentTimeMillis())
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))
        subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(7))

        assertFailsWith<MembershipFrozenException> {
            subscriptionService.useGuestPass(member.id)
        }
    }

    @Test
    fun testGetAllPlansAndGetPlanById() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan1 = subRepo.createPlan(
            Plan("p-all-1-$ts", "Plan 1", MembershipTier.BASIC, 1000, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val plan2 = subRepo.createPlan(
            Plan("p-all-2-$ts", "Plan 2", MembershipTier.PREMIUM, 2000, 30, 2, listOf(GymFeature.POOL))
        )

        val all = subscriptionService.getAllPlans()
        assertTrue(all.any { it.id == "p-all-1-$ts" })
        assertTrue(all.any { it.id == "p-all-2-$ts" })

        val fetched = subscriptionService.getPlanById("p-all-1-$ts")
        assertEquals("Plan 1", fetched.name)

        assertFailsWith<PlanNotFoundException> {
            subscriptionService.getPlanById("unknown-plan")
        }
    }
}
