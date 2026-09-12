using System.Text.Json;

namespace Policy.Infrastructure.Outbox;

public static class OutboxWriter
{
    /// <summary>
    /// Adds an event to the outbox inside the caller's unit of work — the event commits
    /// atomically with the business change (no dual-write, TECH-NOTES.md pitfall #4).
    /// </summary>
    public static void Enqueue<TEvent>(this PolicyDbContext db, TEvent @event, string partitionKey)
        where TEvent : class
    {
        db.OutboxMessages.Add(new OutboxMessage
        {
            EventType = typeof(TEvent).Name,
            Key = partitionKey,
            Payload = JsonSerializer.Serialize(@event),
            CreatedAtUtc = DateTime.UtcNow,
        });
    }
}
