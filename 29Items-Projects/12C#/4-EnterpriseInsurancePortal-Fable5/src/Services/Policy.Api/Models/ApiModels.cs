namespace Policy.Api.Models;

// ── Customers ────────────────────────────────────────────────────────────
public record CreateCustomerRequest(string FirstName, string LastName, string Email, DateOnly DateOfBirth);

public record CustomerDto(Guid Id, string FirstName, string LastName, string Email, DateOnly DateOfBirth);

// ── Quotes ───────────────────────────────────────────────────────────────
public record CreateQuoteRequest(
    Guid CustomerId,
    string BrokerId,
    string ProductCode,
    string StateCode,
    Dictionary<string, string>? RiskFactors);

public record QuoteDto(
    Guid Id,
    Guid CustomerId,
    string BrokerId,
    string ProductCode,
    string StateCode,
    decimal Premium,
    string RateTableVersion,
    string Status,
    DateTime IssuedAtUtc,
    DateTime ExpiresAtUtc);

// ── Policies ─────────────────────────────────────────────────────────────
public record BindPolicyRequest(Guid QuoteId, string BrokerId);

public record CancelPolicyRequest(string Reason);

public record PolicyDto(
    Guid Id,
    string PolicyNumber,
    Guid CustomerId,
    decimal AnnualPremium,
    DateOnly EffectiveDate,
    DateOnly ExpiryDate,
    string Status);

public record PolicyDetailDto(
    Guid Id,
    string PolicyNumber,
    Guid CustomerId,
    string CustomerName,
    decimal AnnualPremium,
    DateOnly EffectiveDate,
    DateOnly ExpiryDate,
    string Status,
    string? CancellationReason,
    IReadOnlyList<ClaimDto> Claims);

// ── Claims ───────────────────────────────────────────────────────────────
public record FileClaimRequest(string Description, decimal ClaimedAmount);

/// <summary>Action: review | approve | reject | pay. ApprovedAmount required for approve.</summary>
public record UpdateClaimStatusRequest(string Action, decimal? ApprovedAmount);

public record ClaimDto(
    Guid Id,
    Guid PolicyId,
    string Description,
    decimal ClaimedAmount,
    decimal? ApprovedAmount,
    string Status,
    DateTime FiledAtUtc,
    DateTime? ResolvedAtUtc);
