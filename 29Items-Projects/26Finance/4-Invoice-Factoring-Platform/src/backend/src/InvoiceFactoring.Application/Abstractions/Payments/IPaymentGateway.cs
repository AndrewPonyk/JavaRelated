using InvoiceFactoring.Domain.ValueObjects;

namespace InvoiceFactoring.Application.Abstractions.Payments;

/// <summary>
/// Abstraction over the payment provider (Stripe). Money-moving calls take an
/// idempotency key so a retry can never disburse twice (see TECH-NOTES §3.6).
/// </summary>
public interface IPaymentGateway
{
    Task<PayoutResult> CreatePayoutAsync(
        string connectedAccountId,
        Money amount,
        string idempotencyKey,
        CancellationToken ct = default);
}

public sealed record PayoutResult(string PayoutId, string Status);
