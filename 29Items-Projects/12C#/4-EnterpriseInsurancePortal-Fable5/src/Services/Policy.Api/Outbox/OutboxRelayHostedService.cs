using Policy.Infrastructure.Outbox;

namespace Policy.Api.Outbox;

/// <summary>
/// Background relay: polls the outbox table and publishes pending events to Kafka.
/// Keeps draining without delay while full batches come back, then idles on the poll interval.
/// </summary>
public class OutboxRelayHostedService(
    IServiceScopeFactory scopeFactory,
    IConfiguration configuration,
    ILogger<OutboxRelayHostedService> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var pollInterval = TimeSpan.FromSeconds(configuration.GetValue("Outbox:PollIntervalSeconds", 2));
        var errorBackoff = TimeSpan.FromSeconds(10);
        logger.LogInformation("Outbox relay started (poll interval {Interval})", pollInterval);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                int published;
                do
                {
                    using var scope = scopeFactory.CreateScope();
                    var processor = scope.ServiceProvider.GetRequiredService<OutboxProcessor>();
                    published = await processor.ProcessPendingAsync(stoppingToken);
                }
                while (published >= OutboxProcessor.BatchSize && !stoppingToken.IsCancellationRequested);

                await Task.Delay(pollInterval, stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception ex)
            {
                // Events stay in the outbox; transient broker/database trouble self-heals on retry.
                logger.LogError(ex, "Outbox relay iteration failed; retrying in {Backoff}", errorBackoff);
                try
                {
                    await Task.Delay(errorBackoff, stoppingToken);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }
        }
    }
}
