package com.gym.service

import com.gym.domain.*
import com.gym.repository.CheckInRepository
import com.gym.repository.MemberRepository
import com.gym.security.AntiPassbackLimiter
import kotlinx.datetime.Clock
import java.util.*

class CheckInService(
    private val memberRepository: MemberRepository,
    private val subscriptionService: SubscriptionService,
    private val checkInRepository: CheckInRepository,
    private val capacityService: CapacityService,
    private val antiPassbackLimiter: AntiPassbackLimiter
) {
    suspend fun processCheckIn(request: CheckInRequest): CheckInResponse {
        request.validate()
        val badge = request.badgeCode.trim().uppercase()
        val now = Clock.System.now()

        // 1. Anti-Passback Rate Limiting (Detects rapid badge sharing)
        antiPassbackLimiter.validateAndRecordScan(badge)

        // 2. Identify Member
        val member = memberRepository.findByBadgeCode(badge)
            ?: throw MemberNotFoundException("Badge $badge is not recognized")

        // 3. Validate Active Subscription & Freeze Status
        val sub = subscriptionService.getActiveSubscription(member.id)
        if (sub.status == SubscriptionStatus.FROZEN) {
            recordFailedCheckIn(member.id, badge, request.zone, request.turnstileId, "Membership is FROZEN")
            throw MembershipFrozenException(member.id)
        }
        if (sub.status != SubscriptionStatus.ACTIVE) {
            recordFailedCheckIn(member.id, badge, request.zone, request.turnstileId, "Subscription is not active (${sub.status})")
            throw NoActiveSubscriptionException(member.id)
        }

        // 4. Feature Gating Check for Zone Access
        val plan = subscriptionService.getPlanById(sub.planId)
        try {
            subscriptionService.validateFeatureGating(plan, request.zone)
        } catch (e: FeatureGatedException) {
            recordFailedCheckIn(member.id, badge, request.zone, request.turnstileId, e.message ?: "Feature gated")
            throw e
        }

        // 5. Facility Capacity Validation
        if (capacityService.isAtCapacity()) {
            recordFailedCheckIn(
                member.id, badge, request.zone, request.turnstileId,
                "Facility at capacity (${capacityService.currentOccupancy}/${capacityService.maxCapacity})"
            )
            throw CapacityExceededException(capacityService.currentOccupancy, capacityService.maxCapacity)
        }

        // 6. Grant Access & Record Audit Log
        val grantedCheckIn = CheckIn(
            id = UUID.randomUUID().toString(),
            memberId = member.id,
            badgeCode = badge,
            zone = request.zone,
            turnstileId = request.turnstileId,
            status = CheckInStatus.GRANTED,
            rejectionReason = null,
            scannedAt = now
        )
        checkInRepository.recordCheckIn(grantedCheckIn)
        val currentOccupancy = capacityService.incrementOccupancy()

        return CheckInResponse(
            accessGranted = true,
            status = CheckInStatus.GRANTED,
            memberId = member.id,
            memberName = member.fullName,
            tier = plan.tier,
            message = "Access granted to ${request.zone.name}. Welcome, ${member.fullName}!",
            remainingGuestPasses = sub.remainingGuestPasses,
            currentCapacity = currentOccupancy,
            maxCapacity = capacityService.maxCapacity
        )
    }

    private suspend fun recordFailedCheckIn(
        memberId: String,
        badgeCode: String,
        zone: GymZone,
        turnstileId: String,
        reason: String
    ) {
        val checkIn = CheckIn(
            id = UUID.randomUUID().toString(),
            memberId = memberId,
            badgeCode = badgeCode,
            zone = zone,
            turnstileId = turnstileId,
            status = CheckInStatus.REJECTED,
            rejectionReason = reason,
            scannedAt = Clock.System.now()
        )
        checkInRepository.recordCheckIn(checkIn)
    }

    suspend fun getRecentCheckIns(memberId: String): List<CheckIn> =
        checkInRepository.getRecentCheckInsByMember(memberId)

    suspend fun getRecentCheckInsPaginated(
        memberId: String,
        page: Int = 1,
        pageSize: Int = 10
    ): PageResponse<CheckIn> {
        val validPage = if (page < 1) 1 else page
        val validPageSize = pageSize.coerceIn(1, 100)
        val offset = ((validPage - 1) * validPageSize).toLong()

        val (items, totalCount) = checkInRepository.getRecentCheckInsByMemberPaginated(memberId, validPageSize, offset)
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

