using System.Text;
using Confluent.Kafka;
using Microsoft.Extensions.Logging;

namespace Portal.Shared.Kafka;

public interface IEventPublisher
{
    /// <param name="key">Partition key — PolicyId for policy-events (per-policy ordering).</param>
    Task PublishAsync(string topic, string key, string payload, string eventType, CancellationToken ct);
}

/// <summary>
/// Kafka producer. In Policy.Api this is driven exclusively by the outbox relay,
/// never directly from request handlers (see ARCHITECTURE.md §2.3).
/// </summary>
public sealed class KafkaEventPublisher : IEventPublisher, IDisposable
{
    public const string EventTypeHeader = "event-type";

    private readonly IProducer<string, string> _producer;

    public KafkaEventPublisher(KafkaOptions options)
    {
        var config = new ProducerConfig
        {
            BootstrapServers = options.BootstrapServers,
            Acks = Acks.All,
            EnableIdempotence = true,
            MessageSendMaxRetries = 5,
        };
        _producer = new ProducerBuilder<string, string>(config).Build();
    }

    public async Task PublishAsync(string topic, string key, string payload, string eventType, CancellationToken ct)
    {
        var message = new Message<string, string>
        {
            Key = key,
            Value = payload,
            Headers = new Headers { { EventTypeHeader, Encoding.UTF8.GetBytes(eventType) } },
        };
        await _producer.ProduceAsync(topic, message, ct);
    }

    public void Dispose() => _producer.Flush(TimeSpan.FromSeconds(10));
}

/// <summary>
/// Null-object publisher used when Kafka is not configured (e.g. a developer running a single
/// service without docker-compose). Events remain durable in the outbox table and are visible
/// in logs; the relay marks them processed so local runs don't accumulate a backlog.
/// </summary>
public sealed class LoggingEventPublisher(ILogger<LoggingEventPublisher> logger) : IEventPublisher
{
    public Task PublishAsync(string topic, string key, string payload, string eventType, CancellationToken ct)
    {
        logger.LogInformation("Kafka disabled — event {EventType} (key {Key}) for topic {Topic}: {Payload}",
            eventType, key, topic, payload);
        return Task.CompletedTask;
    }
}
