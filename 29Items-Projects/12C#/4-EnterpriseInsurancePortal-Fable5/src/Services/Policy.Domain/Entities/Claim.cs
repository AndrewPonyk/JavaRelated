using Policy.Domain.Exceptions;

namespace Policy.Domain.Entities;

public enum ClaimStatus { Filed, UnderReview, Approved, Rejected, Paid }

public class Claim
{
    private static readonly Dictionary<ClaimStatus, ClaimStatus[]> AllowedTransitions = new()
    {
        [ClaimStatus.Filed] = [ClaimStatus.UnderReview],
        [ClaimStatus.UnderReview] = [ClaimStatus.Approved, ClaimStatus.Rejected],
        [ClaimStatus.Approved] = [ClaimStatus.Paid],
        [ClaimStatus.Rejected] = [],
        [ClaimStatus.Paid] = [],
    };

    public Guid Id { get; set; }
    public Guid PolicyId { get; set; }
    public InsurancePolicy? Policy { get; set; }
    public required string Description { get; set; }
    public decimal ClaimedAmount { get; set; }
    public decimal? ApprovedAmount { get; set; }
    public ClaimStatus Status { get; set; } = ClaimStatus.Filed;
    public DateTime FiledAtUtc { get; set; }
    public DateTime? ResolvedAtUtc { get; set; }

    public void StartReview() => TransitionTo(ClaimStatus.UnderReview);

    public void Approve(decimal approvedAmount, DateTime nowUtc)
    {
        if (approvedAmount <= 0)
        {
            throw new DomainException("Approved amount must be positive.");
        }

        if (approvedAmount > ClaimedAmount)
        {
            throw new DomainException(
                $"Approved amount {approvedAmount:0.00} cannot exceed the claimed amount {ClaimedAmount:0.00}.");
        }

        TransitionTo(ClaimStatus.Approved);
        ApprovedAmount = approvedAmount;
        ResolvedAtUtc = nowUtc;
    }

    public void Reject(DateTime nowUtc)
    {
        TransitionTo(ClaimStatus.Rejected);
        ResolvedAtUtc = nowUtc;
    }

    public void MarkPaid() => TransitionTo(ClaimStatus.Paid);

    private void TransitionTo(ClaimStatus target)
    {
        if (!AllowedTransitions[Status].Contains(target))
        {
            throw new InvalidStateTransitionException($"A claim cannot move from {Status} to {target}.");
        }

        Status = target;
    }
}
