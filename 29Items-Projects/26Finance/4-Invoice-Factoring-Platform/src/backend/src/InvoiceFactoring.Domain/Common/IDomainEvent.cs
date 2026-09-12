namespace InvoiceFactoring.Domain.Common;

/// <summary>
/// Marker for something that happened in the domain worth reacting to
/// (e.g. an invoice was submitted). Dispatched after the aggregate is persisted.
/// </summary>
public interface IDomainEvent
{
    DateTimeOffset OccurredOn { get; }
}
