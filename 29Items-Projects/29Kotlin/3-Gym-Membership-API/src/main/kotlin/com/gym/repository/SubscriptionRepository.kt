package com.gym.repository

import com.gym.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface SubscriptionRepository {
    suspend fun getPlanById(planId: String): Plan?
    suspend fun getAllPlans(): List<Plan>
    suspend fun createPlan(plan: Plan): Plan

    suspend fun createSubscription(subscription: Subscription): Subscription
    suspend fun findActiveByMemberId(memberId: String): Subscription?
    suspend fun findById(id: String): Subscription?
    suspend fun updateStatus(id: String, status: SubscriptionStatus): Boolean
    suspend fun updateEndDate(id: String, newEndDate: Instant, cumulativeFreezeDays: Int): Boolean
    suspend fun deductGuestPass(subscriptionId: String): Boolean
    suspend fun findSubscriptionsExpiringBetween(from: Instant, to: Instant): List<Subscription>
    suspend fun markWarningSent(subscriptionId: String, sentAt: Instant): Boolean

    suspend fun createFreezeRecord(record: FreezeRecord): FreezeRecord
    suspend fun getActiveFreeze(subscriptionId: String): FreezeRecord?
    suspend fun completeFreeze(freezeId: String, actualEndDate: Instant): Boolean
    suspend fun getCumulativeFreezeDays(subscriptionId: String, year: Int): Int
    suspend fun findExpiredFreezes(now: Instant): List<FreezeRecord>
    suspend fun hasActiveSubscription(memberId: String): Boolean
}

class ExposedSubscriptionRepository : SubscriptionRepository {

    override suspend fun getPlanById(planId: String): Plan? = newSuspendedTransaction(Dispatchers.IO) {
        PlansTable.selectAll().where { PlansTable.id eq planId }
            .map { it.toPlan() }
            .singleOrNull()
    }

    override suspend fun getAllPlans(): List<Plan> = newSuspendedTransaction(Dispatchers.IO) {
        PlansTable.selectAll().where { PlansTable.isActive eq true }
            .map { it.toPlan() }
    }

    override suspend fun createPlan(plan: Plan): Plan = newSuspendedTransaction(Dispatchers.IO) {
        PlansTable.insert {
            it[id] = plan.id
            it[name] = plan.name
            it[tier] = plan.tier
            it[priceCents] = plan.priceCents
            it[durationDays] = plan.durationDays
            it[maxGuestPasses] = plan.maxGuestPasses
            it[features] = plan.features.joinToString(",") { f -> f.name }
            it[isActive] = plan.isActive
            it[createdAt] = Clock.System.now()
        }
        plan
    }

    override suspend fun createSubscription(subscription: Subscription): Subscription = newSuspendedTransaction(Dispatchers.IO) {
        val now = Clock.System.now()
        SubscriptionsTable.insert {
            it[id] = subscription.id
            it[memberId] = subscription.memberId
            it[planId] = subscription.planId
            it[status] = subscription.status
            it[startDate] = subscription.startDate
            it[endDate] = subscription.endDate
            it[remainingGuestPasses] = subscription.remainingGuestPasses
            it[cumulativeFreezeDays] = subscription.cumulativeFreezeDays
            it[autoRenew] = subscription.autoRenew
            it[lastWarningSentAt] = subscription.lastWarningSentAt
            it[createdAt] = now
            it[updatedAt] = now
        }
        subscription
    }

    override suspend fun findActiveByMemberId(memberId: String): Subscription? = newSuspendedTransaction(Dispatchers.IO) {
        SubscriptionsTable.selectAll()
            .where { (SubscriptionsTable.memberId eq memberId) and (SubscriptionsTable.status inList listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN)) }
            .orderBy(SubscriptionsTable.createdAt to SortOrder.DESC)
            .map { it.toSubscription() }
            .firstOrNull()
    }

    override suspend fun findById(id: String): Subscription? = newSuspendedTransaction(Dispatchers.IO) {
        SubscriptionsTable.selectAll().where { SubscriptionsTable.id eq id }
            .map { it.toSubscription() }
            .singleOrNull()
    }

    override suspend fun updateStatus(id: String, status: SubscriptionStatus): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val count = SubscriptionsTable.update({ SubscriptionsTable.id eq id }) {
            it[SubscriptionsTable.status] = status
            it[updatedAt] = Clock.System.now()
        }
        count > 0
    }

    override suspend fun updateEndDate(id: String, newEndDate: Instant, cumulativeFreezeDays: Int): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val count = SubscriptionsTable.update({ SubscriptionsTable.id eq id }) {
            it[endDate] = newEndDate
            it[SubscriptionsTable.cumulativeFreezeDays] = cumulativeFreezeDays
            it[updatedAt] = Clock.System.now()
        }
        count > 0
    }

    override suspend fun deductGuestPass(subscriptionId: String): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val currentPasses = SubscriptionsTable.selectAll()
            .where { SubscriptionsTable.id eq subscriptionId }
            .map { it[SubscriptionsTable.remainingGuestPasses] }
            .singleOrNull() ?: 0

        if (currentPasses <= 0) return@newSuspendedTransaction false

        val count = SubscriptionsTable.update({ SubscriptionsTable.id eq subscriptionId }) {
            it[remainingGuestPasses] = currentPasses - 1
            it[updatedAt] = Clock.System.now()
        }
        count > 0
    }


    override suspend fun findSubscriptionsExpiringBetween(from: Instant, to: Instant): List<Subscription> = newSuspendedTransaction(Dispatchers.IO) {
        SubscriptionsTable.selectAll()
            .where { 
                (SubscriptionsTable.status eq SubscriptionStatus.ACTIVE) and 
                (SubscriptionsTable.endDate greaterEq from) and 
                (SubscriptionsTable.endDate lessEq to) and
                (SubscriptionsTable.lastWarningSentAt.isNull())
            }
            .map { it.toSubscription() }
    }

    override suspend fun markWarningSent(subscriptionId: String, sentAt: Instant): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val count = SubscriptionsTable.update({ SubscriptionsTable.id eq subscriptionId }) {
            it[lastWarningSentAt] = sentAt
            it[updatedAt] = Clock.System.now()
        }
        count > 0
    }

    override suspend fun createFreezeRecord(record: FreezeRecord): FreezeRecord = newSuspendedTransaction(Dispatchers.IO) {
        FreezeRecordsTable.insert {
            it[id] = record.id
            it[subscriptionId] = record.subscriptionId
            it[memberId] = record.memberId
            it[startDate] = record.startDate
            it[scheduledEndDate] = record.scheduledEndDate
            it[actualEndDate] = record.actualEndDate
            it[daysFrozen] = record.daysFrozen
            it[reason] = record.reason
            it[createdAt] = Clock.System.now()
        }
        record
    }

    override suspend fun getActiveFreeze(subscriptionId: String): FreezeRecord? = newSuspendedTransaction(Dispatchers.IO) {
        FreezeRecordsTable.selectAll()
            .where { (FreezeRecordsTable.subscriptionId eq subscriptionId) and FreezeRecordsTable.actualEndDate.isNull() }
            .map { it.toFreezeRecord() }
            .singleOrNull()
    }

    override suspend fun completeFreeze(freezeId: String, actualEndDate: Instant): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val count = FreezeRecordsTable.update({ FreezeRecordsTable.id eq freezeId }) {
            it[FreezeRecordsTable.actualEndDate] = actualEndDate
        }
        count > 0
    }

    override suspend fun getCumulativeFreezeDays(subscriptionId: String, year: Int): Int = newSuspendedTransaction(Dispatchers.IO) {
        FreezeRecordsTable.selectAll()
            .where { FreezeRecordsTable.subscriptionId eq subscriptionId }
            .sumOf { it[FreezeRecordsTable.daysFrozen] }
    }

    override suspend fun findExpiredFreezes(now: Instant): List<FreezeRecord> = newSuspendedTransaction(Dispatchers.IO) {
        FreezeRecordsTable.selectAll()
            .where { (FreezeRecordsTable.actualEndDate.isNull()) and (FreezeRecordsTable.scheduledEndDate lessEq now) }
            .map { it.toFreezeRecord() }
    }

    override suspend fun hasActiveSubscription(memberId: String): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        SubscriptionsTable.selectAll()
            .where { 
                (SubscriptionsTable.memberId eq memberId) and 
                (SubscriptionsTable.status inList listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN)) 
            }
            .count() > 0
    }


    private fun ResultRow.toPlan() = Plan(
        id = this[PlansTable.id],
        name = this[PlansTable.name],
        tier = this[PlansTable.tier],
        priceCents = this[PlansTable.priceCents],
        durationDays = this[PlansTable.durationDays],
        maxGuestPasses = this[PlansTable.maxGuestPasses],
        features = this[PlansTable.features].split(",")
            .filter { it.isNotBlank() }
            .map { GymFeature.valueOf(it.trim()) },
        isActive = this[PlansTable.isActive]
    )

    private fun ResultRow.toSubscription() = Subscription(
        id = this[SubscriptionsTable.id],
        memberId = this[SubscriptionsTable.memberId],
        planId = this[SubscriptionsTable.planId],
        status = this[SubscriptionsTable.status],
        startDate = this[SubscriptionsTable.startDate],
        endDate = this[SubscriptionsTable.endDate],
        remainingGuestPasses = this[SubscriptionsTable.remainingGuestPasses],
        cumulativeFreezeDays = this[SubscriptionsTable.cumulativeFreezeDays],
        autoRenew = this[SubscriptionsTable.autoRenew],
        lastWarningSentAt = this[SubscriptionsTable.lastWarningSentAt]
    )

    private fun ResultRow.toFreezeRecord() = FreezeRecord(
        id = this[FreezeRecordsTable.id],
        subscriptionId = this[FreezeRecordsTable.subscriptionId],
        memberId = this[FreezeRecordsTable.memberId],
        startDate = this[FreezeRecordsTable.startDate],
        scheduledEndDate = this[FreezeRecordsTable.scheduledEndDate],
        actualEndDate = this[FreezeRecordsTable.actualEndDate],
        daysFrozen = this[FreezeRecordsTable.daysFrozen],
        reason = this[FreezeRecordsTable.reason]
    )
}
