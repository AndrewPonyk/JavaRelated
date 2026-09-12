using MediatR;

namespace InvoiceFactoring.Application.Features.Payments.Commands.ConfirmPayout;

/// <summary>
/// Reconciles a Stripe payout webhook with our records. Idempotent: replays and unknown
/// payout ids are ignored, so Stripe's at-least-once webhooks are safe to process.
/// </summary>
public sealed record ConfirmPayoutCommand(string StripePayoutId, bool Succeeded) : IRequest;
