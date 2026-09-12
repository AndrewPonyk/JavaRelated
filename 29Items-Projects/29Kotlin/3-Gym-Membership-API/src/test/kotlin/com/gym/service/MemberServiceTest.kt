package com.gym.service

import com.gym.domain.*
import com.gym.repository.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class MemberServiceTest {

    private lateinit var memberRepo: ExposedMemberRepository
    private lateinit var memberService: MemberService

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
        memberRepo = ExposedMemberRepository()
        memberService = MemberService(memberRepo)
    }

    @Test
    fun testRegisterSuccess() = runBlocking {
        val request = RegisterMemberRequest(
            email = "john.doe." + System.currentTimeMillis() + "@example.com",
            password = "Password123!",
            fullName = "John Doe",
            badgeCode = "BADGE-JOHN-" + System.currentTimeMillis()
        )
        val member = memberService.register(request)
        assertEquals(request.email.lowercase(), member.email)
        assertEquals("John Doe", member.fullName)
        assertEquals(Role.ROLE_MEMBER, member.role)
    }

    @Test
    fun testRegisterInvalidEmail() = runBlocking {
        val request = RegisterMemberRequest(
            email = "invalid-email-address",
            password = "Password123!",
            fullName = "John Doe",
            badgeCode = "BADGE-INV-02"
        )
        assertFailsWith<ValidationException> {
            memberService.register(request)
        }
    }

    @Test
    fun testRegisterShortPassword() = runBlocking {
        val request = RegisterMemberRequest(
            email = "short.pass@example.com",
            password = "short",
            fullName = "John Doe",
            badgeCode = "BADGE-INV-03"
        )
        assertFailsWith<ValidationException> {
            memberService.register(request)
        }
    }

    @Test
    fun testRegisterBlankFullName() = runBlocking {
        val request = RegisterMemberRequest(
            email = "blank.name@example.com",
            password = "Password123!",
            fullName = "   ",
            badgeCode = "BADGE-INV-04"
        )
        assertFailsWith<ValidationException> {
            memberService.register(request)
        }
    }

    @Test
    fun testRegisterBlankBadge() = runBlocking {
        val request = RegisterMemberRequest(
            email = "blank.badge@example.com",
            password = "Password123!",
            fullName = "John Doe",
            badgeCode = "   "
        )
        assertFailsWith<ValidationException> {
            memberService.register(request)
        }
    }

    @Test
    fun testRegisterDuplicateEmailThrows() = runBlocking {
        val email = "dup" + System.currentTimeMillis() + "@example.com"
        val request1 = RegisterMemberRequest(email, "Password123!", "Dup 1", "BADGE-D1-" + System.currentTimeMillis())
        val request2 = RegisterMemberRequest(email, "Password123!", "Dup 2", "BADGE-D2-" + System.currentTimeMillis())

        memberService.register(request1)
        assertFailsWith<MemberAlreadyExistsException> {
            memberService.register(request2)
        }
    }

    @Test
    fun testRegisterDuplicateBadgeThrows() = runBlocking {
        val badge = "BADGE-SAME-" + System.currentTimeMillis()
        val request1 = RegisterMemberRequest("user1." + System.currentTimeMillis() + "@example.com", "Password123!", "User 1", badge)
        val request2 = RegisterMemberRequest("user2." + System.currentTimeMillis() + "@example.com", "Password123!", "User 2", badge)

        memberService.register(request1)
        assertFailsWith<MemberAlreadyExistsException> {
            memberService.register(request2)
        }
    }

    @Test
    fun testAuthenticateSuccess() = runBlocking {
        val email = "auth.user." + System.currentTimeMillis() + "@example.com"
        memberService.register(
            RegisterMemberRequest(email, "SecretPassword123!", "Auth User", "BADGE-A1-" + System.currentTimeMillis())
        )
        val member = memberService.authenticate(
            LoginRequest(email, "SecretPassword123!")
        )
        assertEquals(email.lowercase(), member.email)
    }

    @Test
    fun testAuthenticateWrongPasswordThrows() = runBlocking {
        val email = "wrong.pass." + System.currentTimeMillis() + "@example.com"
        memberService.register(
            RegisterMemberRequest(email, "CorrectPassword123!", "Wrong Pass", "BADGE-WP-" + System.currentTimeMillis())
        )
        assertFailsWith<InvalidCredentialsException> {
            memberService.authenticate(
                LoginRequest(email, "IncorrectPassword!")
            )
        }
    }

    @Test
    fun testAuthenticateUserNotFoundThrows() = runBlocking {
        assertFailsWith<InvalidCredentialsException> {
            memberService.authenticate(
                LoginRequest("nonexistent@example.com", "Password123!")
            )
        }
    }

    @Test
    fun testGetMemberByIdAndBadge() = runBlocking {
        val badge = "BADGE-LK-" + System.currentTimeMillis()
        val registered = memberService.register(
            RegisterMemberRequest("lookup." + System.currentTimeMillis() + "@example.com", "Password123!", "Lookup Member", badge)
        )

        val byId = memberService.getMemberById(registered.id)
        assertEquals(registered.id, byId.id)

        val byBadge = memberService.getMemberByBadge(badge)
        assertEquals(registered.id, byBadge.id)

        assertFailsWith<MemberNotFoundException> {
            memberService.getMemberById("non-existent-id")
        }

        assertFailsWith<MemberNotFoundException> {
            memberService.getMemberByBadge("NON-EXISTENT-BADGE")
        }
    }

    @Test
    fun testGetMembersPaginated() = runBlocking {
        val ts = System.currentTimeMillis()
        for (i in 1..10) {
            memberService.register(
                RegisterMemberRequest("page$i.$ts@example.com", "Password123!", "User $i", "BADGE-PG-$i-$ts")
            )
        }

        val page1 = memberService.getMembersPaginated(page = 1, pageSize = 5)
        assertTrue(page1.items.isNotEmpty())
        assertEquals(1, page1.page)
        assertEquals(5, page1.pageSize)
        assertTrue(page1.totalCount >= 10)
        assertTrue(page1.totalPages >= 2)

        val page2 = memberService.getMembersPaginated(page = 2, pageSize = 5)
        assertEquals(2, page2.page)
    }
}
