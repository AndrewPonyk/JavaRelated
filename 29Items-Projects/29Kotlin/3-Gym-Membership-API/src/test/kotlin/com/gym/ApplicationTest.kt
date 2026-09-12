package com.gym

import com.gym.domain.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRootDashboardEndpoint() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Gym Membership API & Turnstile Operations Console"))
    }

    @Test
    fun testHealthEndpoint() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\": \"UP\""))
    }

    @Test
    fun testPlansCatalogEndpoint() = testApplication {
        application { module() }
        val response = client.get("/api/v1/plans")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Basic Monthly"))
        assertTrue(response.bodyAsText().contains("VIP Annual"))
    }

    @Test
    fun testPlanDetailsEndpoint() = testApplication {
        application { module() }
        val response = client.get("/api/v1/plans/plan-vip-annual")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("VIP Annual"))

        val notFoundResponse = client.get("/api/v1/plans/non-existent-plan")
        assertEquals(HttpStatusCode.NotFound, notFoundResponse.status)
    }

    @Test
    fun testMemberRegistrationAndLoginFlow() = testApplication {
        application { module() }
        val jsonClient = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Register member
        val regResponse = jsonClient.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterMemberRequest(
                    email = "sarah.crossfit@example.com",
                    password = "SecurePassword123!",
                    fullName = "Sarah CrossFit",
                    badgeCode = "BADGE-SARAH-001"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, regResponse.status)
        val authData: AuthResponse = regResponse.body()
        assertTrue(authData.token.isNotBlank())
        assertEquals("Sarah CrossFit", authData.member.fullName)

        // Login member
        val loginResponse = jsonClient.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequest(
                    email = "sarah.crossfit@example.com",
                    password = "SecurePassword123!"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun testFacilityOccupancyEndpoint() = testApplication {
        application { module() }
        val response = client.get("/api/v1/occupancy")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("maxCapacity"))
    }

    @Test
    fun testFullMemberLifecycleIntegrationFlow() = testApplication {
        application { module() }
        val jsonClient = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Register
        val regRes = jsonClient.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterMemberRequest(
                    email = "lifecycle.user@example.com",
                    password = "SecurePassword123!",
                    fullName = "Lifecycle User",
                    badgeCode = "BADGE-LIFECYCLE-01"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, regRes.status)
        val auth: AuthResponse = regRes.body()
        val token = auth.token
        val memberId = auth.member.id

        // 2. Query Members with Pagination (Authenticated)
        val membersRes = jsonClient.get("/api/v1/members?page=1&pageSize=10") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, membersRes.status)

        // 3. Get member by ID & Badge
        val memberByIdRes = jsonClient.get("/api/v1/members/$memberId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, memberByIdRes.status)

        val memberByBadgeRes = jsonClient.get("/api/v1/members/badge/BADGE-LIFECYCLE-01") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, memberByBadgeRes.status)

        // 4. Create Subscription (VIP plan)
        val subRes = jsonClient.post("/api/v1/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                CreateSubscriptionRequest(
                    memberId = memberId,
                    planId = "plan-vip-annual"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, subRes.status)

        // 5. Query active subscription
        val getSubRes = jsonClient.get("/api/v1/subscriptions/member/$memberId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getSubRes.status)
        val subData: Subscription = getSubRes.body()
        assertEquals(SubscriptionStatus.ACTIVE, subData.status)

        // 6. Turnstile Check-In (Access Granted)
        val checkInRes = jsonClient.post("/api/v1/check-in") {
            contentType(ContentType.Application.Json)
            setBody(
                CheckInRequest(
                    badgeCode = "BADGE-LIFECYCLE-01",
                    zone = GymZone.GYM_FLOOR,
                    turnstileId = "GATE-MAIN-01"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, checkInRes.status)
        val checkInData: CheckInResponse = checkInRes.body()
        assertTrue(checkInData.accessGranted)
        assertEquals(CheckInStatus.GRANTED, checkInData.status)

        // 7. Immediate 2nd Check-in -> 429 Anti-passback violation
        val secondCheckInRes = jsonClient.post("/api/v1/check-in") {
            contentType(ContentType.Application.Json)
            setBody(
                CheckInRequest(
                    badgeCode = "BADGE-LIFECYCLE-01",
                    zone = GymZone.GYM_FLOOR,
                    turnstileId = "GATE-MAIN-01"
                )
            )
        }
        assertEquals(HttpStatusCode.TooManyRequests, secondCheckInRes.status)

        // 8. Freeze Membership
        val freezeRes = jsonClient.post("/api/v1/subscriptions/member/$memberId/freeze") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(FreezeSubscriptionRequest(days = 10, reason = "Travel"))
        }
        assertEquals(HttpStatusCode.OK, freezeRes.status)

        // 9. Unfreeze Membership
        val unfreezeRes = jsonClient.post("/api/v1/subscriptions/member/$memberId/unfreeze") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, unfreezeRes.status)

        // 10. Redeem Guest Pass
        val guestPassRes = jsonClient.post("/api/v1/subscriptions/member/$memberId/guest-pass") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, guestPassRes.status)
        val gpData: GuestPassResponse = guestPassRes.body()
        assertTrue(gpData.remainingGuestPasses >= 0)

        // 11. Query Check-In History
        val historyRes = jsonClient.get("/api/v1/check-ins/member/$memberId?page=1&pageSize=5")
        assertEquals(HttpStatusCode.OK, historyRes.status)
    }

    @Test
    fun testValidationAndAuthErrorResponses() = testApplication {
        application { module() }
        val jsonClient = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Invalid registration email -> 400 Bad Request
        val badReg = jsonClient.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterMemberRequest(
                    email = "not-an-email",
                    password = "pass",
                    fullName = "",
                    badgeCode = ""
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, badReg.status)

        // Unauthenticated access to /api/v1/members -> 401 Unauthorized
        val unauthRes = jsonClient.get("/api/v1/members")
        assertEquals(HttpStatusCode.Unauthorized, unauthRes.status)
    }
}
