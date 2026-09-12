using InvoiceFactoring.Application.Abstractions.Payments;
using InvoiceFactoring.Domain.ValueObjects;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Stripe;

namespace InvoiceFactoring.Infrastructure.Payments.Stripe;

/// <summary>
/// Stripe adapter for the <see cref="IPaymentGateway"/> port. Disburses an advance as a
/// Transfer to the borrower's Connect account. The Stripe idempotency key guarantees a
/// retry of the same logical request never moves money twice (TECH-NOTES §3.6).
/// </summary>
public sealed class StripePaymentGateway : IPaymentGateway
{
    private readonly StripeOptions _options;
    private readonly ILogger<StripePaymentGateway> _logger;

    public StripePaymentGateway(IOptions<StripeOptions> options, ILogger<StripePaymentGateway> logger)
    {
        _options = options.Value;
        _logger = logger;
        // TODO: prefer DI of a typed StripeClient over the static global key.
        StripeConfiguration.ApiKey = _options.SecretKey;
        StripeConfiguration.MaxNetworkRetries = 2; // Stripe's built-in safe retries
    }

    public async Task<PayoutResult> CreatePayoutAsync(
        string connectedAccountId,
        Money amount,
        string idempotencyKey,
        CancellationToken ct = default)
    {
        var transferOptions = new TransferCreateOptions
        {
            Amount = ToMinorUnits(amount),          // Stripe expects integer minor units
            Currency = amount.Currency.ToLowerInvariant(),
            Destination = connectedAccountId,
            Description = "Invoice factoring advance"
        };

        var requestOptions = new RequestOptions { IdempotencyKey = idempotencyKey };

        _logger.LogInformation("Creating Stripe transfer of {Amount} to {Account} (idem {Key}).",
            amount, connectedAccountId, idempotencyKey);

        var service = new TransferService();
        Transfer transfer = await service.CreateAsync(transferOptions, requestOptions, ct);

        // TODO: handle StripeException → map to a transient vs. permanent failure for Polly.
        return new PayoutResult(transfer.Id, transfer.Object ?? "transfer");
    }

    private static long ToMinorUnits(Money money) => (long)decimal.Round(money.Amount * 100m, 0);
}
