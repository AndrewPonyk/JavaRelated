using InvoiceFactoring.Application.Features.Advances.Dtos;
using MediatR;

namespace InvoiceFactoring.Application.Features.Advances.Commands.AcceptOffer;

/// <summary>
/// Borrower accepts the advance offer for an approved invoice. <paramref name="IdempotencyKey"/>
/// is supplied by the caller (and forwarded to Stripe) so retries can never disburse twice.
/// </summary>
public sealed record AcceptAdvanceOfferCommand(Guid InvoiceId, string IdempotencyKey)
    : IRequest<AdvanceDto>;
