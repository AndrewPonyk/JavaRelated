package com.gym.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    ROLE_MEMBER,
    ROLE_TRAINER,
    ROLE_ADMIN
}

@Serializable
enum class MembershipTier {
    BASIC,
    PREMIUM,
    VIP
}

@Serializable
enum class GymZone {
    GYM_FLOOR,
    POOL,
    SAUNA,
    VIP_LOUNGE,
    CLASS_STUDIO
}

@Serializable
enum class GymFeature {
    GYM_FLOOR,
    LOCKER_ROOM,
    POOL,
    SAUNA,
    GROUP_CLASSES,
    VIP_LOUNGE,
    PERSONAL_TRAINER_CONSULT,
    FREE_TOWEL_SERVICE
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    FROZEN,
    EXPIRED,
    CANCELLED
}

@Serializable
enum class CheckInStatus {
    GRANTED,
    REJECTED
}

@Serializable
data class Member(
    val id: String,
    val email: String,
    val fullName: String,
    val badgeCode: String,
    val role: Role = Role.ROLE_MEMBER,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val tier: MembershipTier,
    val priceCents: Int,
    val durationDays: Int,
    val maxGuestPasses: Int,
    val features: List<GymFeature>,
    val isActive: Boolean = true
)

@Serializable
data class Subscription(
    val id: String,
    val memberId: String,
    val planId: String,
    val status: SubscriptionStatus,
    val startDate: Instant,
    val endDate: Instant,
    val remainingGuestPasses: Int,
    val cumulativeFreezeDays: Int = 0,
    val autoRenew: Boolean = true,
    val lastWarningSentAt: Instant? = null
)

@Serializable
data class FreezeRecord(
    val id: String,
    val subscriptionId: String,
    val memberId: String,
    val startDate: Instant,
    val scheduledEndDate: Instant,
    val actualEndDate: Instant? = null,
    val daysFrozen: Int,
    val reason: String? = null
)

@Serializable
data class CheckIn(
    val id: String,
    val memberId: String,
    val badgeCode: String,
    val zone: GymZone,
    val turnstileId: String,
    val status: CheckInStatus,
    val rejectionReason: String? = null,
    val scannedAt: Instant
)

// Request & Response DTOs

@Serializable
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int
)

@Serializable
data class RegisterMemberRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val badgeCode: String,
    val role: Role = Role.ROLE_MEMBER
) {
    fun validate() {
        if (email.isBlank()) throw ValidationException("Email cannot be blank")
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (!emailRegex.matches(email.trim())) throw ValidationException("Invalid email format: $email")
        if (password.length < 8) throw ValidationException("Password must be at least 8 characters long")
        if (fullName.isBlank()) throw ValidationException("Full name cannot be blank")
        if (badgeCode.isBlank()) throw ValidationException("Badge code cannot be blank")
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
) {
    fun validate() {
        if (email.isBlank()) throw ValidationException("Email cannot be blank")
        if (password.isBlank()) throw ValidationException("Password cannot be blank")
    }
}

@Serializable
data class AuthResponse(
    val token: String,
    val member: Member
)

@Serializable
data class CreateSubscriptionRequest(
    val memberId: String,
    val planId: String,
    val autoRenew: Boolean = true
) {
    fun validate() {
        if (memberId.isBlank()) throw ValidationException("Member ID cannot be blank")
        if (planId.isBlank()) throw ValidationException("Plan ID cannot be blank")
    }
}

@Serializable
data class FreezeSubscriptionRequest(
    val days: Int,
    val reason: String? = null
) {
    fun validate() {
        if (days !in 1..30) throw ValidationException("Freeze duration must be between 1 and 30 days")
    }
}

@Serializable
data class CheckInRequest(
    val badgeCode: String,
    val zone: GymZone = GymZone.GYM_FLOOR,
    val turnstileId: String = "GATE-01"
) {
    fun validate() {
        if (badgeCode.isBlank()) throw ValidationException("Badge code cannot be blank")
        if (turnstileId.isBlank()) throw ValidationException("Turnstile ID cannot be blank")
    }
}

@Serializable
data class CheckInResponse(
    val accessGranted: Boolean,
    val status: CheckInStatus,
    val memberId: String? = null,
    val memberName: String? = null,
    val tier: MembershipTier? = null,
    val message: String,
    val remainingGuestPasses: Int? = null,
    val currentCapacity: Int,
    val maxCapacity: Int
)

@Serializable
data class GuestPassResponse(
    val message: String,
    val remainingGuestPasses: Int
)

@Serializable
data class OccupancyResponse(
    val currentOccupancy: Int,
    val maxCapacity: Int,
    val isAtCapacity: Boolean
)

@Serializable
data class ExpiryWebhookPayload(
    val event: String = "SUBSCRIPTION_EXPIRING",
    val memberId: String,
    val subscriptionId: String,
    val daysRemaining: Int,
    val expirationDate: String,
    val autoRenew: Boolean
)

