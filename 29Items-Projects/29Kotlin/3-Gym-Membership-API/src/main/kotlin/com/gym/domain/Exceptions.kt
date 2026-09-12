package com.gym.domain

sealed class GymException(message: String) : RuntimeException(message)

class MemberNotFoundException(id: String) : GymException("Member not found with ID: $id")
class MemberAlreadyExistsException(email: String) : GymException("Member already exists with email or badge: $email")
class InvalidCredentialsException : GymException("Invalid email or password")
class PlanNotFoundException(planId: String) : GymException("Membership plan not found: $planId")
class SubscriptionNotFoundException(id: String) : GymException("Subscription not found: $id")
class NoActiveSubscriptionException(memberId: String) : GymException("Member $memberId has no active subscription")
class MembershipFrozenException(memberId: String) : GymException("Membership for member $memberId is currently FROZEN")
class FreezeLimitExceededException(message: String) : GymException(message)
class FeatureGatedException(tier: MembershipTier, zone: GymZone) : 
    GymException("Tier $tier does not have access to zone $zone")
class CapacityExceededException(current: Int, max: Int) : 
    GymException("Gym facility is currently at maximum capacity ($current/$max)")
class BadgeSharingDetectedException(badgeCode: String, remainingMinutes: Long) : 
    GymException("Anti-passback alert: Badge $badgeCode was scanned recently. Please wait $remainingMinutes minutes before re-scanning.")
class InsufficientGuestPassesException : GymException("No guest passes remaining in current billing cycle")
class ValidationException(message: String) : GymException(message)
class DuplicateSubscriptionException(memberId: String) : 
    GymException("Member $memberId already has an active subscription")
class UnauthorizedAccessException(message: String = "Forbidden: Insufficient privileges") : GymException(message)

