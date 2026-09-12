using InvoiceFactoring.Application.Abstractions.Messaging;

namespace InvoiceFactoring.IntegrationTests;

/// <summary>Swallows integration events — there is no Service Bus in the test host.</summary>
public sealed class NoOpIntegrationEventPublisher : IIntegrationEventPublisher
{
    public Task PublishAsync<T>(T message, CancellationToken ct = default) where T : class
        => Task.CompletedTask;
}
