package com.gym.service

import com.gym.domain.*
import com.gym.repository.*
import com.gym.security.InMemoryAntiPassbackLimiter
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class CheckInServiceTest {

    private lateinit var checkInService: CheckInService
    private lateinit var memberService: MemberService
    private lateinit var subscriptionService: SubscriptionService
    private lateinit var capacityService: CapacityService
    private lateinit var antiPassbackLimiter: InMemoryAntiPassbackLimiter

    private val memberRepo = ExposedMemberRepository()
    private val subRepo = ExposedSubscriptionRepository()
    private val checkInRepo = ExposedCheckInRepository()

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
        capacityService = InMemoryCapacityService(maxLimit = 2) // Small limit for testing
        antiPassbackLimiter = InMemoryAntiPassbackLimiter(cooldownMinutes = 15)

        checkInService = CheckInService(
            memberRepository = memberRepo,
            subscriptionService = subscriptionService,
            checkInRepository = checkInRepo,
            capacityService = capacityService,
            antiPassbackLimiter = antiPassbackLimiter
        )
    }

    @Test
    fun testSuccessfulCheckInIncrementsOccupancy() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan("plan-vip-$ts", "VIP", MembershipTier.VIP, 59999, 365, 5, listOf(GymFeature.GYM_FLOOR, GymFeature.POOL))
        )
        val badge = "BADGE-CHK-$ts"
        val member = memberService.register(
            RegisterMemberRequest("checkin.$ts@test.com", "Pass1234!", "Checkin User", badge)
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))

        val response = checkInService.processCheckIn(
            CheckInRequest(badge, GymZone.GYM_FLOOR)
        )

        assertTrue(response.accessGranted)
        assertEquals(CheckInStatus.GRANTED, response.status)
        assertEquals(1, capacityService.currentOccupancy)
    }

    @Test
    fun testCapacityExceededRejectsEntry() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan("plan-basic-$ts", "Basic", MembershipTier.BASIC, 2999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val b1 = "BADGE-M1-$ts"
        val b2 = "BADGE-M2-$ts"
        val b3 = "BADGE-M3-$ts"
        val m1 = memberService.register(RegisterMemberRequest("m1.$ts@test.com", "P1Pass1234!", "M1", b1))
        val m2 = memberService.register(RegisterMemberRequest("m2.$ts@test.com", "P2Pass1234!", "M2", b2))
        val m3 = memberService.register(RegisterMemberRequest("m3.$ts@test.com", "P3Pass1234!", "M3", b3))

        subscriptionService.createSubscription(CreateSubscriptionRequest(m1.id, plan.id))
        subscriptionService.createSubscription(CreateSubscriptionRequest(m2.id, plan.id))
        subscriptionService.createSubscription(CreateSubscriptionRequest(m3.id, plan.id))

        // First two take up capacity (maxLimit = 2)
        checkInService.processCheckIn(CheckInRequest(b1))
        checkInService.processCheckIn(CheckInRequest(b2))

        // Third check-in must fail with CapacityExceededException
        assertFailsWith<CapacityExceededException> {
            checkInService.processCheckIn(CheckInRequest(b3))
        }
    }

    @Test
    fun testUnknownBadgeRejectsCheckIn() = runBlocking {
        assertFailsWith<MemberNotFoundException> {
            checkInService.processCheckIn(CheckInRequest("BADGE-UNKNOWN-999"))
        }
    }

    @Test
    fun testFrozenMembershipRejectsCheckIn() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan("plan-chk-frz-$ts", "Basic", MembershipTier.BASIC, 2999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val badge = "BADGE-FRZ-$ts"
        val member = memberService.register(
            RegisterMemberRequest("chk.frz.$ts@test.com", "Password123!", "Chk Frz", badge)
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))
        subscriptionService.freezeMembership(member.id, FreezeSubscriptionRequest(7))

        assertFailsWith<MembershipFrozenException> {
            checkInService.processCheckIn(CheckInRequest(badge))
        }
    }

    @Test
    fun testZoneFeatureGatingRejectsCheckIn() = runBlocking {
        val ts = System.currentTimeMillis()
        val basicPlan = subRepo.createPlan(
            Plan("plan-gated-$ts", "Basic Only", MembershipTier.BASIC, 2999, 30, 0, listOf(GymFeature.GYM_FLOOR))
        )
        val badge = "BADGE-GATED-$ts"
        val member = memberService.register(
            RegisterMemberRequest("gated.$ts@test.com", "Password123!", "Gated User", badge)
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, basicPlan.id))

        // Attempting to check into POOL with basic plan must throw FeatureGatedException
        assertFailsWith<FeatureGatedException> {
            checkInService.processCheckIn(CheckInRequest(badge, GymZone.POOL))
        }
    }

    @Test
    fun testAntiPassbackRejectsImmediateRescan() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan("plan-apb-$ts", "VIP", MembershipTier.VIP, 59999, 365, 5, listOf(GymFeature.GYM_FLOOR))
        )
        val badge = "BADGE-APB-$ts"
        val member = memberService.register(
            RegisterMemberRequest("apb.$ts@test.com", "Password123!", "APB User", badge)
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))

        // First scan succeeds
        val res = checkInService.processCheckIn(CheckInRequest(badge))
        assertTrue(res.accessGranted)

        // Immediate second scan throws BadgeSharingDetectedException
        assertFailsWith<BadgeSharingDetectedException> {
            checkInService.processCheckIn(CheckInRequest(badge))
        }
    }

    @Test
    fun testGetRecentCheckInsPaginated() = runBlocking {
        val ts = System.currentTimeMillis()
        val plan = subRepo.createPlan(
            Plan("plan-hist-$ts", "VIP", MembershipTier.VIP, 59999, 365, 5, listOf(GymFeature.GYM_FLOOR))
        )
        val badge = "BADGE-HIST-$ts"
        val member = memberService.register(
            RegisterMemberRequest("hist.$ts@test.com", "Password123!", "Hist User", badge)
        )
        subscriptionService.createSubscription(CreateSubscriptionRequest(member.id, plan.id))
        capacityService.setMaxCapacity(100)

        // Perform 5 check-ins (resetting limiter in-between)
        for (i in 1..5) {
            antiPassbackLimiter.reset(badge)
            checkInService.processCheckIn(CheckInRequest(badge, turnstileId = "GATE-0$i"))
        }

        val page1 = checkInService.getRecentCheckInsPaginated(member.id, page = 1, pageSize = 2)
        assertEquals(2, page1.items.size)
        assertEquals(1, page1.page)
        assertEquals(2, page1.pageSize)
        assertEquals(5L, page1.totalCount)
        assertEquals(3, page1.totalPages)

        val historyList = checkInService.getRecentCheckIns(member.id)
        assertEquals(5, historyList.size)
    }
}
