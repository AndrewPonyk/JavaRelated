using System.Text;
using Confluent.Kafka;
using Microsoft.AspNetCore.SignalR;
using Portal.Shared.Kafka;
using Portal.Web.Hubs;

namespace Portal.Web.Eventing;

/// <summary>
/// Bridges Kafka policy-events to SignalR: every event triggers a broadcast so open
/// broker screens refresh live (ARCHITECTURE.md §2.3). UI push is best-effort — a
/// failed message is logged and skipped, never blocking the partition.
/// </summary>
public class PolicyEventsKafkaConsumer(
    KafkaOptions options,
    IHubContext<PolicyHub> hubContext,
    ILogger<PolicyEventsKafkaConsumer> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!options.IsConfigured)
        {
            logger.LogInformation("Kafka not configured — live policy updates disabled");
            return;
        }

        // The blocking Consume loop gets its own thread so it never starves the host.
        await Task.Run(() => ConsumeLoop(stoppingToken), stoppingToken);
    }

    private void ConsumeLoop(CancellationToken stoppingToken)
    {
        var config = new ConsumerConfig
        {
            BootstrapServers = options.BootstrapServers,
            GroupId = "portal-web",
            AutoOffsetReset = AutoOffsetReset.Latest, // UI only cares about new events
            EnableAutoCommit = true,
            PartitionAssignmentStrategy = PartitionAssignmentStrategy.CooperativeSticky, // pitfall #8
        };

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                using var consumer = new ConsumerBuilder<string, string>(config).Build();
                consumer.Subscribe(options.PolicyEventsTopic);
                logger.LogInformation("Subscribed to {Topic} for live policy updates", options.PolicyEventsTopic);

                while (!stoppingToken.IsCancellationRequested)
                {
                    var result = consumer.Consume(stoppingToken);
                    if (result?.Message is null)
                    {
                        continue;
                    }

                    var eventType = result.Message.Headers.TryGetLastBytes(
                        KafkaEventPublisher.EventTypeHeader, out var bytes)
                        ? Encoding.UTF8.GetString(bytes)
                        : "unknown";

                    hubContext.Clients.All
                        .SendAsync(PolicyHub.PolicyUpdatedMethod, eventType, stoppingToken)
                        .GetAwaiter().GetResult();
                }
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Kafka consumer failed; reconnecting in 5s");
                if (stoppingToken.WaitHandle.WaitOne(TimeSpan.FromSeconds(5)))
                {
                    return;
                }
            }
        }
    }
}
