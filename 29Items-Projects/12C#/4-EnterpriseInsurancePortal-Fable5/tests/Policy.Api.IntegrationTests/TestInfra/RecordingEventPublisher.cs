using System.Collections.Concurrent;
using Portal.Shared.Kafka;

namespace Policy.Api.IntegrationTests.TestInfra;

public record PublishedEvent(string Topic, string Key, string Payload, string EventType);

/// <summary>Captures everything the outbox relay would have sent to Kafka.</summary>
public class RecordingEventPublisher : IEventPublisher
{
    private readonly ConcurrentQueue<PublishedEvent> _events = new();

    public IReadOnlyCollection<PublishedEvent> Events => [.. _events];

    public Task PublishAsync(string topic, string key, string payload, string eventType, CancellationToken ct)
    {
        _events.Enqueue(new PublishedEvent(topic, key, payload, eventType));
        return Task.CompletedTask;
    }
}
