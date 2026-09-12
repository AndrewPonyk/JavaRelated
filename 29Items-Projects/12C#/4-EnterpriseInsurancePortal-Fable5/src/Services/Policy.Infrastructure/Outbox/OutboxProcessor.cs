using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Portal.Shared.Kafka;

namespace Policy.Infrastructure.Outbox;

/// <summary>
/// Relays unprocessed outbox rows to Kafka. At-least-once: a crash between publish
/// and mark-processed re-publishes, so consumers must be idempotent (keyed by event id/PolicyId).
/// </summary>
public class OutboxProcessor(
    IDbContextFactory<PolicyDbContext> dbFactory,
    IEventPublisher publisher,
    KafkaOptions kafkaOptions,
    ILogger<OutboxProcessor> logger)
{
    public const int BatchSize = 100;

    /// <summary>Processes one batch. Returns the number of messages published.</summary>
    public async Task<int> ProcessPendingAsync(CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var pending = await db.OutboxMessages
            .Where(m => m.ProcessedAtUtc == null)
            .OrderBy(m => m.Id)
            .Take(BatchSize)
            .ToListAsync(ct);

        foreach (var message in pending)
        {
            await publisher.PublishAsync(
                kafkaOptions.PolicyEventsTopic, message.Key, message.Payload, message.EventType, ct);
            message.ProcessedAtUtc = DateTime.UtcNow;
            // Mark per message so a mid-batch crash re-publishes at most the in-flight one.
            await db.SaveChangesAsync(ct);
        }

        if (pending.Count > 0)
        {
            logger.LogInformation("Outbox relay published {Count} event(s)", pending.Count);
        }

        return pending.Count;
    }
}
