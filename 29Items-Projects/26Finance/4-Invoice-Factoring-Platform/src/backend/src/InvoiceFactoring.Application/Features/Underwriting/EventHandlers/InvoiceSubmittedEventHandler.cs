using InvoiceFactoring.Application.Abstractions.Messaging;
using InvoiceFactoring.Application.Common.Events;
using InvoiceFactoring.Application.Features.Underwriting.Contracts;
using InvoiceFactoring.Domain.Events;
using MediatR;
using Microsoft.Extensions.Logging;

namespace InvoiceFactoring.Application.Features.Underwriting.EventHandlers;

/// <summary>
/// Bridges the in-process <see cref="InvoiceSubmittedEvent"/> domain event to an
/// asynchronous integration event on the Service Bus, so underwriting runs off the
/// request thread (see ARCHITECTURE §2.3).
/// </summary>
public sealed class InvoiceSubmittedEventHandler
    : INotificationHandler<DomainEventNotification<InvoiceSubmittedEvent>>
{
    private readonly IIntegrationEventPublisher _publisher;
    private readonly ILogger<InvoiceSubmittedEventHandler> _logger;

    public InvoiceSubmittedEventHandler(
        IIntegrationEventPublisher publisher,
        ILogger<InvoiceSubmittedEventHandler> logger)
    {
        _publisher = publisher;
        _logger = logger;
    }

    public async Task Handle(
        DomainEventNotification<InvoiceSubmittedEvent> notification,
        CancellationToken cancellationToken)
    {
        var e = notification.DomainEvent;
        _logger.LogInformation("Invoice {InvoiceId} submitted; queueing for underwriting.", e.InvoiceId);

        await _publisher.PublishAsync(
            new UnderwriteInvoiceMessage(e.InvoiceId, e.CompanyId),
            cancellationToken);
    }
}
