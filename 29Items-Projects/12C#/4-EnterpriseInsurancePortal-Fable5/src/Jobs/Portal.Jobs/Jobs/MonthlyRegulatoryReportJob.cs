using System.Text.Json;
using Hangfire;
using Microsoft.EntityFrameworkCore;
using Policy.Domain.Entities;
using Policy.Infrastructure;

namespace Portal.Jobs.Jobs;

public record RegulatoryReport(
    int Year,
    int Month,
    int PoliciesBound,
    int PoliciesCancelled,
    decimal TotalAnnualPremiumBound,
    int ClaimsFiled,
    int ClaimsApproved,
    int ClaimsRejected,
    decimal TotalApprovedClaimAmount,
    int AuditedMutations,
    DateTime GeneratedAtUtc);

/// <summary>
/// Generates the monthly regulatory report from the policy store, cross-checks the
/// figures against the immutable audit trail, and writes the report to the configured
/// reports directory (an Azure Blob mount in AKS).
/// </summary>
public class MonthlyRegulatoryReportJob(
    IDbContextFactory<PolicyDbContext> dbFactory,
    IConfiguration configuration,
    ILogger<MonthlyRegulatoryReportJob> logger)
{
    [DisableConcurrentExecution(timeoutInSeconds: 7200)]
    [AutomaticRetry(Attempts = 2)]
    public async Task RunAsync(CancellationToken ct)
    {
        // Reports always cover the previous full calendar month.
        var now = DateTime.UtcNow;
        var periodStart = new DateTime(now.Year, now.Month, 1, 0, 0, 0, DateTimeKind.Utc).AddMonths(-1);
        var periodEnd = periodStart.AddMonths(1);
        var report = await BuildReportAsync(periodStart, periodEnd, ct);

        var reportsPath = configuration["Reports:OutputPath"] ?? "reports";
        Directory.CreateDirectory(reportsPath);
        var filePath = Path.Combine(reportsPath, $"regulatory-{report.Year:0000}-{report.Month:00}.json");
        await File.WriteAllTextAsync(filePath,
            JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true }), ct);

        logger.LogInformation(
            "Regulatory report {Year}-{Month:00} written to {Path}: {Bound} bound, {Cancelled} cancelled, {Filed} claims filed",
            report.Year, report.Month, filePath, report.PoliciesBound, report.PoliciesCancelled, report.ClaimsFiled);
    }

    public async Task<RegulatoryReport> BuildReportAsync(DateTime periodStartUtc, DateTime periodEndUtc, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var startDate = DateOnly.FromDateTime(periodStartUtc);
        var endDate = DateOnly.FromDateTime(periodEndUtc);

        var boundPolicies = await db.Policies.AsNoTracking()
            .Where(p => p.EffectiveDate >= startDate && p.EffectiveDate < endDate)
            .Select(p => p.AnnualPremium)
            .ToListAsync(ct);

        var policiesCancelled = await db.Policies.AsNoTracking()
            .CountAsync(p => p.CancelledAtUtc >= periodStartUtc && p.CancelledAtUtc < periodEndUtc, ct);

        var claims = await db.Claims.AsNoTracking()
            .Where(c => c.FiledAtUtc >= periodStartUtc && c.FiledAtUtc < periodEndUtc)
            .Select(c => new { c.Status, c.ApprovedAmount })
            .ToListAsync(ct);

        // Regulatory requirement: report figures must reconcile against the audit trail.
        var auditBound = await db.AuditEntries.AsNoTracking()
            .CountAsync(a => a.Action == "PolicyBound"
                && a.OccurredAtUtc >= periodStartUtc && a.OccurredAtUtc < periodEndUtc, ct);
        if (auditBound != boundPolicies.Count)
        {
            logger.LogError(
                "Audit reconciliation mismatch for {Start:yyyy-MM}: {AuditCount} PolicyBound audit rows vs {StoreCount} policies",
                periodStartUtc, auditBound, boundPolicies.Count);
        }

        var auditedMutations = await db.AuditEntries.AsNoTracking()
            .CountAsync(a => a.OccurredAtUtc >= periodStartUtc && a.OccurredAtUtc < periodEndUtc, ct);

        return new RegulatoryReport(
            periodStartUtc.Year,
            periodStartUtc.Month,
            PoliciesBound: boundPolicies.Count,
            PoliciesCancelled: policiesCancelled,
            TotalAnnualPremiumBound: boundPolicies.Sum(),
            ClaimsFiled: claims.Count,
            ClaimsApproved: claims.Count(c => c.Status is ClaimStatus.Approved or ClaimStatus.Paid),
            ClaimsRejected: claims.Count(c => c.Status == ClaimStatus.Rejected),
            TotalApprovedClaimAmount: claims.Sum(c => c.ApprovedAmount ?? 0m),
            AuditedMutations: auditedMutations,
            GeneratedAtUtc: DateTime.UtcNow);
    }
}
