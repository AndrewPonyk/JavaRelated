using Policy.Domain.Exceptions;

namespace Policy.Domain.Entities;

public enum PolicyStatus { Active, Lapsed, Cancelled, Expired }

// Named InsurancePolicy to avoid clashing with the Policy.* namespace.
public class InsurancePolicy
{
    public Guid Id { get; set; }
    public required string PolicyNumber { get; set; }
    public Guid QuoteId { get; set; }
    public Quote? Quote { get; set; }
    public Guid CustomerId { get; set; }
    public Customer? Customer { get; set; }
    public decimal AnnualPremium { get; set; }
    public DateOnly EffectiveDate { get; set; }
    public DateOnly ExpiryDate { get; set; }
    public PolicyStatus Status { get; set; }
    public string? CancellationReason { get; set; }
    public DateTime? CancelledAtUtc { get; set; }

    public ICollection<Claim> Claims { get; set; } = [];

    public void Cancel(string reason, DateTime nowUtc)
    {
        if (Status != PolicyStatus.Active)
        {
            throw new InvalidStateTransitionException($"Only active policies can be cancelled (current: {Status}).");
        }

        if (string.IsNullOrWhiteSpace(reason))
        {
            throw new DomainException("A cancellation reason is required.");
        }

        Status = PolicyStatus.Cancelled;
        CancellationReason = reason.Trim();
        CancelledAtUtc = nowUtc;
    }

    public Claim FileClaim(string description, decimal claimedAmount, DateTime nowUtc)
    {
        if (Status != PolicyStatus.Active)
        {
            throw new InvalidStateTransitionException($"Claims can only be filed against active policies (current: {Status}).");
        }

        if (claimedAmount <= 0)
        {
            throw new DomainException("Claimed amount must be positive.");
        }

        var claim = new Claim
        {
            Id = Guid.NewGuid(),
            PolicyId = Id,
            Description = description.Trim(),
            ClaimedAmount = claimedAmount,
            Status = ClaimStatus.Filed,
            FiledAtUtc = nowUtc,
        };
        Claims.Add(claim);
        return claim;
    }

    /// <summary>Applies a recalculated premium (nightly Hangfire job). Returns true when it changed.</summary>
    public bool ApplyRecalculatedPremium(decimal newPremium)
    {
        if (newPremium <= 0)
        {
            throw new DomainException("A recalculated premium must be positive.");
        }

        if (Status != PolicyStatus.Active || newPremium == AnnualPremium)
        {
            return false;
        }

        AnnualPremium = newPremium;
        return true;
    }
}
