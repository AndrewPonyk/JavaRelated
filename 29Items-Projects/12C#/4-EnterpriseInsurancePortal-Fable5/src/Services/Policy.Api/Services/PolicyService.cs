using Microsoft.EntityFrameworkCore;
using Policy.Api.Auth;
using Policy.Api.Models;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Policy.Infrastructure;
using Policy.Infrastructure.Outbox;
using Portal.Shared.Contracts.Events;

namespace Policy.Api.Services;

public class PolicyService(
    IDbContextFactory<PolicyDbContext> dbFactory,
    ICurrentUser currentUser,
    ILogger<PolicyService> logger) : IPolicyService
{
    public async Task<PagedResult<PolicyDto>> ListAsync(Guid? customerId, Paging paging, CancellationToken ct)
    {
        // Customers may only ever see their own policies, whatever filter they pass.
        if (currentUser.IsCustomer)
        {
            customerId = currentUser.CustomerId ?? Guid.Empty;
        }

        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var query = db.Policies.AsNoTracking()
            .Where(p => customerId == null || p.CustomerId == customerId);
        var total = await query.CountAsync(ct);
        var items = await query
            .OrderByDescending(p => p.EffectiveDate).ThenBy(p => p.Id)
            .Skip(paging.Skip).Take(paging.PageSize)
            .Select(p => new PolicyDto(
                p.Id, p.PolicyNumber, p.CustomerId, p.AnnualPremium,
                p.EffectiveDate, p.ExpiryDate, p.Status.ToString()))
            .ToListAsync(ct);
        return new PagedResult<PolicyDto>(items, total);
    }

    public async Task<PolicyDetailDto?> GetByIdAsync(Guid id, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var policy = await db.Policies.AsNoTracking()
            .Include(p => p.Customer)
            .Include(p => p.Claims)
            .FirstOrDefaultAsync(p => p.Id == id, ct);

        if (policy is null || (currentUser.IsCustomer && policy.CustomerId != currentUser.CustomerId))
        {
            return null; // a foreign policy is indistinguishable from a missing one
        }

        return new PolicyDetailDto(
            policy.Id, policy.PolicyNumber, policy.CustomerId,
            policy.Customer?.FullName ?? string.Empty,
            policy.AnnualPremium, policy.EffectiveDate, policy.ExpiryDate,
            policy.Status.ToString(), policy.CancellationReason,
            [.. policy.Claims
                .OrderByDescending(c => c.FiledAtUtc)
                .Select(c => new ClaimDto(c.Id, c.PolicyId, c.Description, c.ClaimedAmount,
                    c.ApprovedAmount, c.Status.ToString(), c.FiledAtUtc, c.ResolvedAtUtc))]);
    }

    public async Task<PolicyDto> BindAsync(BindPolicyRequest request, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var quote = await db.Quotes.FirstOrDefaultAsync(q => q.Id == request.QuoteId, ct)
            ?? throw new EntityNotFoundException(nameof(Quote), request.QuoteId);

        var now = DateTime.UtcNow;
        var policy = quote.Bind(now);
        db.Policies.Add(policy);

        // Event + audit commit atomically with the policy (transactional outbox, ARCHITECTURE.md §2.2)
        db.Enqueue(
            new PolicyBoundEvent(policy.Id, policy.PolicyNumber, policy.CustomerId, policy.AnnualPremium, now),
            policy.Id.ToString());
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "PolicyBound",
            EntityName = nameof(InsurancePolicy),
            EntityId = policy.Id,
            Actor = currentUser.Name,
            Details = $"quote={quote.Id};premium={policy.AnnualPremium:0.00}",
            OccurredAtUtc = now,
        });

        await db.SaveChangesAsync(ct);
        logger.LogInformation("Policy {PolicyNumber} bound from quote {QuoteId} by {Actor}",
            policy.PolicyNumber, quote.Id, currentUser.Name);

        return new PolicyDto(policy.Id, policy.PolicyNumber, policy.CustomerId,
            policy.AnnualPremium, policy.EffectiveDate, policy.ExpiryDate, policy.Status.ToString());
    }

    public async Task<PolicyDto> CancelAsync(Guid id, string reason, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var policy = await db.Policies.FirstOrDefaultAsync(p => p.Id == id, ct)
            ?? throw new EntityNotFoundException(nameof(InsurancePolicy), id);

        var now = DateTime.UtcNow;
        policy.Cancel(reason, now);

        db.Enqueue(
            new PolicyCancelledEvent(policy.Id, policy.PolicyNumber, policy.CancellationReason!, now),
            policy.Id.ToString());
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "PolicyCancelled",
            EntityName = nameof(InsurancePolicy),
            EntityId = policy.Id,
            Actor = currentUser.Name,
            Details = $"reason={policy.CancellationReason}",
            OccurredAtUtc = now,
        });

        await db.SaveChangesAsync(ct);
        logger.LogInformation("Policy {PolicyNumber} cancelled by {Actor}: {Reason}",
            policy.PolicyNumber, currentUser.Name, reason);

        return new PolicyDto(policy.Id, policy.PolicyNumber, policy.CustomerId,
            policy.AnnualPremium, policy.EffectiveDate, policy.ExpiryDate, policy.Status.ToString());
    }
}
