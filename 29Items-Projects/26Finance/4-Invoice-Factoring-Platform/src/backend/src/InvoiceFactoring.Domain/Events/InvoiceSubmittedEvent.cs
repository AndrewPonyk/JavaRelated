using InvoiceFactoring.Domain.Common;

namespace InvoiceFactoring.Domain.Events;

/// <summary>
/// Raised when a borrower submits an invoice. The Underwriting context consumes this
/// (via Service Bus) to enrich features, score default risk, and produce an offer.
/// </summary>
public sealed record InvoiceSubmittedEvent(Guid InvoiceId, Guid CompanyId) : IDomainEvent
{
    public DateTimeOffset OccurredOn { get; } = DateTimeOffset.UtcNow;
}
