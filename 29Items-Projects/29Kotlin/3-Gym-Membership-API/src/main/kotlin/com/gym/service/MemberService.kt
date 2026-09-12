package com.gym.service

import com.gym.domain.*
import com.gym.repository.MemberRepository
import com.gym.security.PasswordHasher
import kotlinx.datetime.Clock
import java.util.*

class MemberService(
    private val memberRepository: MemberRepository
) {
    suspend fun register(request: RegisterMemberRequest): Member {
        request.validate()

        val normalizedEmail = request.email.trim().lowercase()
        val normalizedBadge = request.badgeCode.trim().uppercase()

        memberRepository.findByEmail(normalizedEmail)?.let {
            throw MemberAlreadyExistsException(normalizedEmail)
        }
        memberRepository.findByBadgeCode(normalizedBadge)?.let {
            throw MemberAlreadyExistsException("Badge $normalizedBadge is already assigned")
        }

        val now = Clock.System.now()
        val member = Member(
            id = UUID.randomUUID().toString(),
            email = normalizedEmail,
            fullName = request.fullName.trim(),
            badgeCode = normalizedBadge,
            role = request.role,
            createdAt = now,
            updatedAt = now
        )

        val hash = PasswordHasher.hash(request.password)
        return memberRepository.create(member, hash)
    }

    suspend fun authenticate(request: LoginRequest): Member {
        request.validate()
        val normalizedEmail = request.email.trim().lowercase()
        val member = memberRepository.findByEmail(normalizedEmail)
            ?: throw InvalidCredentialsException()

        val valid = memberRepository.verifyPassword(member.email, request.password)
        if (!valid) throw InvalidCredentialsException()

        return member
    }

    suspend fun getMemberById(id: String): Member {
        return memberRepository.findById(id) ?: throw MemberNotFoundException(id)
    }

    suspend fun getMemberByBadge(badgeCode: String): Member {
        val normalizedBadge = badgeCode.trim().uppercase()
        return memberRepository.findByBadgeCode(normalizedBadge)
            ?: throw MemberNotFoundException("badge: $normalizedBadge")
    }

    suspend fun getAllMembers(): List<Member> {
        return memberRepository.getAll()
    }

    suspend fun getMembersPaginated(page: Int = 1, pageSize: Int = 20): PageResponse<Member> {
        val validPage = if (page < 1) 1 else page
        val validPageSize = pageSize.coerceIn(1, 100)
        val offset = ((validPage - 1) * validPageSize).toLong()

        val (items, totalCount) = memberRepository.getPaginated(validPageSize, offset)
        val totalPages = if (totalCount == 0L) 1 else Math.ceil(totalCount.toDouble() / validPageSize).toInt()

        return PageResponse(
            items = items,
            page = validPage,
            pageSize = validPageSize,
            totalCount = totalCount,
            totalPages = totalPages
        )
    }
}

