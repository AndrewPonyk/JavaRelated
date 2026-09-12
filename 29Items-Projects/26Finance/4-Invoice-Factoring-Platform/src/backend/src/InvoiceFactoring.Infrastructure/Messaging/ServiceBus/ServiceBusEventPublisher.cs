using System.Collections.Concurrent;
using System.Text.Json;
using Azure.Messaging.ServiceBus;
using InvoiceFactoring.Application.Abstractions.Messaging;
using InvoiceFactoring.Application.Features.Underwriting.Contracts;
using Microsoft.Extensions.Options;

namespace InvoiceFactoring.Infrastructure.Messaging.ServiceBus;

/// <summary>
/// Publishes integration events to Azure Service Bus. Routes by message type to the
/// appropriate queue and caches senders (creating one per call is wasteful).
/// </summary>
public sealed class ServiceBusEventPublisher : IIntegrationEventPublisher, IAsyncDisposable
{
    private readonly ServiceBusClient _client;
    private readonly ServiceBusOptions _options;
    private readonly ConcurrentDictionary<string, ServiceBusSender> _senders = new();

    public ServiceBusEventPublisher(ServiceBusClient client, IOptions<ServiceBusOptions> options)
    {
        _client = client;
        _options = options.Value;
    }

    public async Task PublishAsync<T>(T message, CancellationToken ct = default) where T : class
    {
        var queue = ResolveQueue(message);
        var sender = _senders.GetOrAdd(queue, _client.CreateSender);

        var sbMessage = new ServiceBusMessage(JsonSerializer.SerializeToUtf8Bytes(message))
        {
            ContentType = "application/json",
            Subject = typeof(T).Name,                 // used by the consumer to deserialize
            MessageId = Guid.NewGuid().ToString()     // de-dupe hint for the consumer
        };

        await sender.SendMessageAsync(sbMessage, ct);
    }

    private string ResolveQueue<T>(T message) => message switch
    {
        UnderwriteInvoiceMessage => _options.UnderwritingQueue,
        // TODO: add payment/notification message routing as those flows are built.
        _ => _options.UnderwritingQueue
    };

    public async ValueTask DisposeAsync()
    {
        foreach (var sender in _senders.Values)
            await sender.DisposeAsync();
    }
}
