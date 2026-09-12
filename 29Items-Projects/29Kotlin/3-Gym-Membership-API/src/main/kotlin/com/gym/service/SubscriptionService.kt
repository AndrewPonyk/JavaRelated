package com.gym.service

import com.gym.domain.*
import com.gym.repository.MemberRepository
import com.gym.repository.SubscriptionRepository
import kotlinx.datetime.*
import java.util.*
import kotlin.time.Duration.Companion.days

class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val memberRepository: MemberRepository
) {
    companion object {
        const val MAX_ANNUAL_FREEZE_DAYS = 30
    }

    suspend fun getAllPlans(): List<Plan> = subscriptionRepository.getAllPlans()

    suspend fun getPlanById(planId: String): Plan =
        subscriptionRepository.getPlanById(planId) ?: throw PlanNotFoundException(planId)

    suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription {
        request.validate()

        // 1. Verify member exists
        memberRepository.findById(request.memberId)
            ?: throw MemberNotFoundException(request.memberId)

        // 2. Verify no existing active/frozen subscription
        if (subscriptionRepository.hasActiveSubscription(request.memberId)) {
            throw DuplicateSubscriptionException(request.memberId)
        }

        // 3. Verify plan exists
        val plan = getPlanById(request.planId)
        val now = Clock.System.now()
        val endDate = now.plus(plan.durationDays.days)

        val sub = Subscription(
            id = UUID.randomUUID().toString(),
            memberId = request.memberId,
            planId = plan.id,
            status = SubscriptionStatus.ACTIVE,
            startDate = now,
            endDate = endDate,
            remainingGuestPasses = plan.maxGuestPasses,
            cumulativeFreezeDays = 0,
            autoRenew = request.autoRenew
        )
        return subscriptionRepository.createSubscription(sub)
    }


    suspend fun getActiveSubscription(memberId: String): Subscription {
        val sub = subscriptionRepository.findActiveByMemberId(memberId)
            ?: throw NoActiveSubscriptionException(memberId)

        val now = Clock.System.now()
        if (sub.status == SubscriptionStatus.ACTIVE && sub.endDate < now) {
            subscriptionRepository.updateStatus(sub.id, SubscriptionStatus.EXPIRED)
            return sub.copy(status = SubscriptionStatus.EXPIRED)
        }
        return sub
    }

    suspend fun freezeMembership(memberId: String, request: FreezeSubscriptionRequest): FreezeRecord {
        request.validate()
        val sub = getActiveSubscription(memberId)
        if (sub.status == SubscriptionStatus.FROZEN) {
            throw FreezeLimitExceededException("Membership is already currently FROZEN")
        }

        if (request.days !in 1..MAX_ANNUAL_FREEZE_DAYS) {
            throw ValidationException("Freeze duration must be between 1 and $MAX_ANNUAL_FREEZE_DAYS days")
        }


        val year = Clock.System.now().toLocalDateTime(TimeZone.UTC).year
        val usedDays = subscriptionRepository.getCumulativeFreezeDays(sub.id, year)
        val totalDaysRequested = usedDays + request.days

        if (totalDaysRequested > MAX_ANNUAL_FREEZE_DAYS) {
            val remainingAllowance = (MAX_ANNUAL_FREEZE_DAYS - usedDays).coerceAtLeast(0)
            throw FreezeLimitExceededException(
                "Freeze limit exceeded. You have used $usedDays/$MAX_ANNUAL_FREEZE_DAYS days. " +
                "Only $remainingAllowance freeze days remaining for this calendar year."
            )
        }

        val now = Clock.System.now()
        val scheduledEnd = now.plus(request.days.days)
        val extendedSubscriptionEnd = sub.endDate.plus(request.days.days)

        val freezeRecord = FreezeRecord(
            id = UUID.randomUUID().toString(),
            subscriptionId = sub.id,
            memberId = memberId,
            startDate = now,
            scheduledEndDate = scheduledEnd,
            actualEndDate = null,
            daysFrozen = request.days,
            reason = request.reason
        )

        subscriptionRepository.createFreezeRecord(freezeRecord)
        subscriptionRepository.updateStatus(sub.id, SubscriptionStatus.FROZEN)
        subscriptionRepository.updateEndDate(sub.id, extendedSubscriptionEnd, totalDaysRequested)

        return freezeRecord
    }

    suspend fun unfreezeMembership(memberId: String): Subscription {
        val sub = getActiveSubscription(memberId)
        if (sub.status != SubscriptionStatus.FROZEN) {
            throw IllegalArgumentException("Membership is not currently frozen")
        }

        val activeFreeze = subscriptionRepository.getActiveFreeze(sub.id)
        if (activeFreeze != null) {
            subscriptionRepository.completeFreeze(activeFreeze.id, Clock.System.now())
        }

        subscriptionRepository.updateStatus(sub.id, SubscriptionStatus.ACTIVE)
        return sub.copy(status = SubscriptionStatus.ACTIVE)
    }

    suspend fun useGuestPass(memberId: String): Int {
        val sub = getActiveSubscription(memberId)
        if (sub.status != SubscriptionStatus.ACTIVE) {
            throw MembershipFrozenException(memberId)
        }
        if (sub.remainingGuestPasses <= 0) {
            throw InsufficientGuestPassesException()
        }

        val success = subscriptionRepository.deductGuestPass(sub.id)
        if (!success) throw InsufficientGuestPassesException()

        return sub.remainingGuestPasses - 1
    }

    fun validateFeatureGating(plan: Plan, requestedZone: GymZone) {
        val requiredFeature = when (requestedZone) {
            GymZone.GYM_FLOOR -> GymFeature.GYM_FLOOR
            GymZone.POOL -> GymFeature.POOL
            GymZone.SAUNA -> GymFeature.SAUNA
            GymZone.VIP_LOUNGE -> GymFeature.VIP_LOUNGE
            GymZone.CLASS_STUDIO -> GymFeature.GROUP_CLASSES
        }

        if (!plan.features.contains(requiredFeature)) {
            throw FeatureGatedException(plan.tier, requestedZone)
        }
    }
}
