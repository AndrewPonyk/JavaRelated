using InvoiceFactoring.Domain.Entities;

namespace InvoiceFactoring.Application.Features.Advances.Dtos;

public sealed record AdvanceDto(
    Guid Id,
    Guid InvoiceId,
    decimal NetDisbursed,
    decimal Fee,
    decimal Reserve,
    string Currency,
    string Status,
    string? StripePayoutId)
{
    public static AdvanceDto FromEntity(Advance a) => new(
        a.Id, a.InvoiceId,
        a.NetDisbursed.Amount, a.Fee.Amount, a.Reserve.Amount,
        a.NetDisbursed.Currency, a.Status.ToString(), a.StripePayoutId);
}
