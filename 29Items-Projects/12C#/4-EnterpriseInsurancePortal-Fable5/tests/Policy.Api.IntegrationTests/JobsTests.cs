using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Policy.Api.IntegrationTests.TestInfra;
using Policy.Domain.Entities;
using Portal.Jobs.Jobs;
using Portal.Jobs.Rating;
using Xunit;

namespace Policy.Api.IntegrationTests;

/// <summary>Drives the Hangfire job classes directly (Hangfire is only their scheduler).</summary>
public class JobsTests : IDisposable
{
    private sealed class FixedRatingClient(decimal premium) : IRatingClient
    {
        public int Calls { get; private set; }

        public Task<RatingOutcome> RateAsync(
            string productCode, string stateCode, IReadOnlyDictionary<string, string> riskFactors, CancellationToken ct)
        {
            Calls++;
            return Task.FromResult(new RatingOutcome(premium, "job-test-v2"));
        }
    }

    private readonly SqliteDb _db = new();
    private static readonly DateTime Now = DateTime.UtcNow;

    private async Task<InsurancePolicy> SeedBoundPolicyAsync(decimal premium, PolicyStatus status = PolicyStatus.Active)
    {
        await using var db = _db.CreateDbContext();
        var customer = Customer.Create("Job", "Tester", $"{Guid.NewGuid():N}@example.com",
            new DateOnly(1985, 5, 5), DateOnly.FromDateTime(Now));
        var quote = Quote.Issue(customer.Id, "BRK-001", "AUTO-STD", "CA",
            """{"driverAge":"40"}""", premium, "2026.06", Now);
        var policy = quote.Bind(Now);
        policy.Status = status;
        db.Customers.Add(customer);
        db.Quotes.Add(quote);
        db.Policies.Add(policy);
        db.AuditEntries.Add(new Policy.Infrastructure.AuditEntry
        {
            Action = "PolicyBound",
            EntityName = nameof(InsurancePolicy),
            EntityId = policy.Id,
            Actor = "seed",
            OccurredAtUtc = Now,
        });
        await db.SaveChangesAsync();
        return policy;
    }

    [Fact]
    public async Task NightlyRecalculation_UpdatesChangedPremiums_AndEmitsEvents()
    {
        var policy = await SeedBoundPolicyAsync(premium: 1200m);
        var rating = new FixedRatingClient(premium: 1450m);
        var job = new NightlyPremiumCalculationJob(_db, rating, NullLogger<NightlyPremiumCalculationJob>.Instance);

        await job.RunAsync(CancellationToken.None);

        await using var db = _db.CreateDbContext();
        db.Policies.Single(p => p.Id == policy.Id).AnnualPremium.Should().Be(1450m);
        var outbox = db.OutboxMessages.Single(m => m.EventType == "PremiumRecalculatedEvent");
        outbox.Key.Should().Be(policy.Id.ToString());
        outbox.Payload.Should().Contain("1200").And.Contain("1450");
        db.AuditEntries.Count(a => a.Action == "PremiumRecalculated").Should().Be(1);
    }

    [Fact]
    public async Task NightlyRecalculation_IsIdempotent_WhenRatesAreUnchanged()
    {
        await SeedBoundPolicyAsync(premium: 1450m);
        var rating = new FixedRatingClient(premium: 1450m);
        var job = new NightlyPremiumCalculationJob(_db, rating, NullLogger<NightlyPremiumCalculationJob>.Instance);

        await job.RunAsync(CancellationToken.None);
        await job.RunAsync(CancellationToken.None);

        await using var db = _db.CreateDbContext();
        db.OutboxMessages.Should().BeEmpty("unchanged premiums must not generate events");
        rating.Calls.Should().Be(2, "every active policy is re-rated on every run");
    }

    [Fact]
    public async Task NightlyRecalculation_SkipsCancelledPolicies()
    {
        await SeedBoundPolicyAsync(premium: 1200m, status: PolicyStatus.Cancelled);
        var rating = new FixedRatingClient(premium: 9999m);
        var job = new NightlyPremiumCalculationJob(_db, rating, NullLogger<NightlyPremiumCalculationJob>.Instance);

        await job.RunAsync(CancellationToken.None);

        rating.Calls.Should().Be(0);
    }

    [Fact]
    public async Task MonthlyReport_AggregatesPolicyAndClaimFigures_ForThePeriod()
    {
        var policy = await SeedBoundPolicyAsync(premium: 1200m);
        await using (var db = _db.CreateDbContext())
        {
            var tracked = db.Policies.Single(p => p.Id == policy.Id);
            var claim = tracked.FileClaim("Windshield", 600m, Now);
            db.Claims.Add(claim); // claim keys are client-generated — explicit Add required
            claim.StartReview();
            claim.Approve(550m, Now);
            await db.SaveChangesAsync();
        }

        var job = new MonthlyRegulatoryReportJob(
            _db, new ConfigurationBuilder().Build(), NullLogger<MonthlyRegulatoryReportJob>.Instance);

        var periodStart = new DateTime(Now.Year, Now.Month, 1, 0, 0, 0, DateTimeKind.Utc);
        var report = await job.BuildReportAsync(periodStart, periodStart.AddMonths(1), CancellationToken.None);

        report.PoliciesBound.Should().Be(1);
        report.TotalAnnualPremiumBound.Should().Be(1200m);
        report.ClaimsFiled.Should().Be(1);
        report.ClaimsApproved.Should().Be(1);
        report.TotalApprovedClaimAmount.Should().Be(550m);
        report.PoliciesCancelled.Should().Be(0);
        report.AuditedMutations.Should().BeGreaterThan(0);
    }

    public void Dispose() => _db.Dispose();
}
