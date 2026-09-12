namespace InvoiceFactoring.Domain.Common;

/// <summary>
/// An aggregate root is the only entry point for mutating its cluster of entities and
/// the only thing repositories load/save. It records domain events to be dispatched
/// after the unit of work commits.
/// </summary>
public abstract class AggregateRoot : Entity
{
    private readonly List<IDomainEvent> _domainEvents = new();

    public IReadOnlyCollection<IDomainEvent> DomainEvents => _domainEvents.AsReadOnly();

    protected void Raise(IDomainEvent domainEvent) => _domainEvents.Add(domainEvent);

    public void ClearDomainEvents() => _domainEvents.Clear();
}
