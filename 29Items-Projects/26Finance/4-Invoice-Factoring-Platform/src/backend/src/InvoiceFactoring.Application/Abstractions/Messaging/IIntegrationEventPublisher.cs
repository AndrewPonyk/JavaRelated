namespace InvoiceFactoring.Application.Abstractions.Messaging;

/// <summary>
/// Publishes integration events/commands to the message broker (Azure Service Bus) so
/// slow work (underwriting, notifications) runs asynchronously off the request thread.
/// </summary>
public interface IIntegrationEventPublisher
{
    Task PublishAsync<T>(T message, CancellationToken ct = default) where T : class;
}
