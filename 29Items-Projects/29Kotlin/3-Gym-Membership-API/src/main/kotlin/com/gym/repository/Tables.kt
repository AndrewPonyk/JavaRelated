package com.gym.repository

import com.gym.domain.GymZone
import com.gym.domain.MembershipTier
import com.gym.domain.Role
import com.gym.domain.SubscriptionStatus
import com.gym.domain.CheckInStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MembersTable : Table("members") {
    val id = varchar("id", 36)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fullName = varchar("full_name", 255)
    val badgeCode = varchar("badge_code", 100).uniqueIndex()
    val role = enumerationByName("role", 50, Role::class)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PlansTable : Table("plans") {
    val id = varchar("id", 50)
    val name = varchar("name", 100)
    val tier = enumerationByName("tier", 20, MembershipTier::class)
    val priceCents = integer("price_cents")
    val durationDays = integer("duration_days")
    val maxGuestPasses = integer("max_guest_passes")
    val features = text("features") // Comma-separated or JSON list
    val isActive = bool("is_active")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object SubscriptionsTable : Table("subscriptions") {
    val id = varchar("id", 36)
    val memberId = varchar("member_id", 36).references(MembersTable.id)
    val planId = varchar("plan_id", 50).references(PlansTable.id)
    val status = enumerationByName("status", 20, SubscriptionStatus::class)
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val remainingGuestPasses = integer("remaining_guest_passes")
    val cumulativeFreezeDays = integer("cumulative_freeze_days")
    val autoRenew = bool("auto_renew")
    val lastWarningSentAt = timestamp("last_warning_sent_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, memberId, status)
        index(false, endDate)
    }
}

object FreezeRecordsTable : Table("freeze_records") {
    val id = varchar("id", 36)
    val subscriptionId = varchar("subscription_id", 36).references(SubscriptionsTable.id)
    val memberId = varchar("member_id", 36).references(MembersTable.id)
    val startDate = timestamp("start_date")
    val scheduledEndDate = timestamp("scheduled_end_date")
    val actualEndDate = timestamp("actual_end_date").nullable()
    val daysFrozen = integer("days_frozen")
    val reason = varchar("reason", 255).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, subscriptionId)
        index(false, scheduledEndDate)
    }
}

object CheckInsTable : Table("check_ins") {
    val id = varchar("id", 36)
    val memberId = varchar("member_id", 36).references(MembersTable.id)
    val badgeCode = varchar("badge_code", 100)
    val zone = enumerationByName("zone", 50, GymZone::class)
    val turnstileId = varchar("turnstile_id", 50)
    val status = enumerationByName("status", 20, CheckInStatus::class)
    val rejectionReason = varchar("rejection_reason", 255).nullable()
    val scannedAt = timestamp("scanned_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, memberId, scannedAt)
        index(false, badgeCode)
    }
}

