using System.Text.Json;
using Hangfire;
using Microsoft.EntityFrameworkCore;
using Policy.Domain.Entities;
using Policy.Infrastructure;
using Policy.Infrastructure.Outbox;
using Portal.Jobs.Rating;
using Portal.Shared.Contracts.Events;

namespace Portal.Jobs.Jobs;

/// <summary>
/// Recalculates premiums for all active policies against the current rate table.
/// Idempotent and resumable: keyset-paginated batches, each committed independently,
/// so a pod restart resumes from the last committed batch (TECH-NOTES.md pitfall #10).
/// </summary>
public class NightlyPremiumCalculationJob(
    IDbContextFactory<PolicyDbContext> dbFactory,
    IRatingClient ratingClient,
    ILogger<NightlyPremiumCalculationJob> logger)
{
    public const int BatchSize = 500;

    [DisableConcurrentExecution(timeoutInSeconds: 3600)] // multiple pods — TECH-NOTES.md pitfall #5
    [AutomaticRetry(Attempts = 3)]
    public async Task RunAsync(CancellationToken ct)
    {
        var started = DateTime.UtcNow;
        logger.LogInformation("Nightly premium recalculation started");

        var lastId = Guid.Empty;
        int scanned = 0, changed = 0;

        while (!ct.IsCancellationRequested)
        {
            await using var db = await dbFactory.CreateDbContextAsync(ct);

            var batch = await db.Policies
                .Include(p => p.Quote)
                .Where(p => p.Status == PolicyStatus.Active && p.Id > lastId)
                .OrderBy(p => p.Id) // keyset pagination — stable across restarts
                .Take(BatchSize)
                .ToListAsync(ct);

            if (batch.Count == 0)
            {
                break;
            }

            foreach (var policy in batch)
            {
                var quote = policy.Quote;
                if (quote is null)
                {
                    logger.LogWarning("Policy {PolicyNumber} has no originating quote; skipped", policy.PolicyNumber);
                    continue;
                }

                var riskFactors = JsonSerializer.Deserialize<Dictionary<string, string>>(quote.RiskFactorsJson) ?? [];
                var rating = await ratingClient.RateAsync(quote.ProductCode, quote.StateCode, riskFactors, ct);

                var oldPremium = policy.AnnualPremium;
                if (policy.ApplyRecalculatedPremium(rating.AnnualPremium))
                {
                    changed++;
                    db.Enqueue(
                        new PremiumRecalculatedEvent(policy.Id, policy.PolicyNumber, oldPremium,
                            rating.AnnualPremium, DateTime.UtcNow),
                        policy.Id.ToString());
                    db.AuditEntries.Add(new AuditEntry
                    {
                        Action = "PremiumRecalculated",
                        EntityName = nameof(InsurancePolicy),
                        EntityId = policy.Id,
                        Actor = "system:nightly-premium-job",
                        Details = $"old={oldPremium:0.00};new={rating.AnnualPremium:0.00};rateTable={rating.RateTableVersion}",
                        OccurredAtUtc = DateTime.UtcNow,
                    });
                }
            }

            scanned += batch.Count;
            lastId = batch[^1].Id; // checkpoint
            await db.SaveChangesAsync(ct);
        }

        logger.LogInformation(
            "Nightly premium recalculation finished in {Duration:c}: {Scanned} active policies scanned, {Changed} premiums updated",
            DateTime.UtcNow - started, scanned, changed);
    }
}
