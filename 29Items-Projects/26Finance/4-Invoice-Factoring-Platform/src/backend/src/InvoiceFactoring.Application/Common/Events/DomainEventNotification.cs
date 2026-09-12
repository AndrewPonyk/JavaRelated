using InvoiceFactoring.Domain.Common;
using MediatR;

namespace InvoiceFactoring.Application.Common.Events;

/// <summary>
/// Wraps a pure <see cref="IDomainEvent"/> as a MediatR <see cref="INotification"/> so the
/// DbContext can dispatch domain events in-process AFTER the unit of work commits, without
/// the Domain layer taking a dependency on MediatR.
/// </summary>
public sealed record DomainEventNotification<TDomainEvent>(TDomainEvent DomainEvent) : INotification
    where TDomainEvent : IDomainEvent;
