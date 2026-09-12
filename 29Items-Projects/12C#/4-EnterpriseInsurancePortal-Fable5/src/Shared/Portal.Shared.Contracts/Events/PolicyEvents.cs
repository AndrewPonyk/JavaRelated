namespace Portal.Shared.Contracts.Events;

// Kafka topic: policy-events. Partition key = PolicyId (preserves per-policy ordering).
// Contracts evolve additively only — add fields, never remove/rename (independent consumers).

public record PolicyBoundEvent(
    Guid PolicyId,
    string PolicyNumber,
    Guid CustomerId,
    decimal AnnualPremium,
    DateTime OccurredAtUtc);

public record PolicyCancelledEvent(
    Guid PolicyId,
    string PolicyNumber,
    string Reason,
    DateTime OccurredAtUtc);

public record ClaimFiledEvent(
    Guid ClaimId,
    Guid PolicyId,
    decimal ClaimedAmount,
    DateTime OccurredAtUtc);

public record ClaimStatusChangedEvent(
    Guid ClaimId,
    Guid PolicyId,
    string NewStatus,
    decimal? ApprovedAmount,
    DateTime OccurredAtUtc);

/// <summary>Published by the nightly Hangfire recalculation job for each changed premium.</summary>
public record PremiumRecalculatedEvent(
    Guid PolicyId,
    string PolicyNumber,
    decimal OldAnnualPremium,
    decimal NewAnnualPremium,
    DateTime OccurredAtUtc);
