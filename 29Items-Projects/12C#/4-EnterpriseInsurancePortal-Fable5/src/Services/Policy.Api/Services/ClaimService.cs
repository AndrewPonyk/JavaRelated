using Microsoft.EntityFrameworkCore;
using Policy.Api.Auth;
using Policy.Api.Models;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Policy.Infrastructure;
using Policy.Infrastructure.Outbox;
using Portal.Shared.Contracts.Events;

namespace Policy.Api.Services;

public class ClaimService(
    IDbContextFactory<PolicyDbContext> dbFactory,
    ICurrentUser currentUser,
    ILogger<ClaimService> logger) : IClaimService
{
    public async Task<IReadOnlyList<ClaimDto>> ListForPolicyAsync(Guid policyId, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        await EnsurePolicyVisible(db, policyId, ct);

        return await db.Claims.AsNoTracking()
            .Where(c => c.PolicyId == policyId)
            .OrderByDescending(c => c.FiledAtUtc)
            .Select(c => ToDto(c))
            .ToListAsync(ct);
    }

    public async Task<ClaimDto> FileAsync(Guid policyId, FileClaimRequest request, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var policy = await db.Policies.FirstOrDefaultAsync(p => p.Id == policyId, ct);
        if (policy is null || (currentUser.IsCustomer && policy.CustomerId != currentUser.CustomerId))
        {
            throw new EntityNotFoundException(nameof(InsurancePolicy), policyId);
        }

        var now = DateTime.UtcNow;
        var claim = policy.FileClaim(request.Description, request.ClaimedAmount, now);
        // Explicit Add: the claim's Guid key is set client-side, so relying on navigation
        // discovery would track it as Modified instead of Added.
        db.Claims.Add(claim);

        db.Enqueue(
            new ClaimFiledEvent(claim.Id, policy.Id, claim.ClaimedAmount, now),
            policy.Id.ToString());
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "ClaimFiled",
            EntityName = nameof(Claim),
            EntityId = claim.Id,
            Actor = currentUser.Name,
            Details = $"policy={policy.Id};amount={claim.ClaimedAmount:0.00}",
            OccurredAtUtc = now,
        });

        await db.SaveChangesAsync(ct);
        logger.LogInformation("Claim {ClaimId} filed against policy {PolicyNumber} for {Amount}",
            claim.Id, policy.PolicyNumber, claim.ClaimedAmount);
        return ToDto(claim);
    }

    public async Task<ClaimDto> UpdateStatusAsync(Guid claimId, UpdateClaimStatusRequest request, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var claim = await db.Claims.FirstOrDefaultAsync(c => c.Id == claimId, ct)
            ?? throw new EntityNotFoundException(nameof(Claim), claimId);

        var now = DateTime.UtcNow;
        switch (request.Action.ToLowerInvariant())
        {
            case "review":
                claim.StartReview();
                break;
            case "approve":
                claim.Approve(request.ApprovedAmount
                    ?? throw new DomainException("approvedAmount is required when approving a claim."), now);
                break;
            case "reject":
                claim.Reject(now);
                break;
            case "pay":
                claim.MarkPaid();
                break;
            default:
                throw new DomainException($"Unknown claim action '{request.Action}'. Use review, approve, reject or pay.");
        }

        db.Enqueue(
            new ClaimStatusChangedEvent(claim.Id, claim.PolicyId, claim.Status.ToString(), claim.ApprovedAmount, now),
            claim.PolicyId.ToString());
        db.AuditEntries.Add(new AuditEntry
        {
            Action = $"Claim{claim.Status}",
            EntityName = nameof(Claim),
            EntityId = claim.Id,
            Actor = currentUser.Name,
            Details = claim.ApprovedAmount is { } approved ? $"approvedAmount={approved:0.00}" : null,
            OccurredAtUtc = now,
        });

        await db.SaveChangesAsync(ct);
        return ToDto(claim);
    }

    private async Task EnsurePolicyVisible(PolicyDbContext db, Guid policyId, CancellationToken ct)
    {
        var owner = await db.Policies.AsNoTracking()
            .Where(p => p.Id == policyId)
            .Select(p => (Guid?)p.CustomerId)
            .FirstOrDefaultAsync(ct);

        if (owner is null || (currentUser.IsCustomer && owner != currentUser.CustomerId))
        {
            throw new EntityNotFoundException(nameof(InsurancePolicy), policyId);
        }
    }

    private static ClaimDto ToDto(Claim c) => new(
        c.Id, c.PolicyId, c.Description, c.ClaimedAmount, c.ApprovedAmount,
        c.Status.ToString(), c.FiledAtUtc, c.ResolvedAtUtc);
}
