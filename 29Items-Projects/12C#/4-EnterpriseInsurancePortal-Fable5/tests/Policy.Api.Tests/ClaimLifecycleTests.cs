using FluentAssertions;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Xunit;

namespace Policy.Api.Tests;

public class ClaimLifecycleTests
{
    private static readonly DateTime Now = new(2026, 6, 12, 10, 0, 0, DateTimeKind.Utc);

    private static InsurancePolicy ActivePolicy() => new()
    {
        Id = Guid.NewGuid(),
        PolicyNumber = "POL-TEST-1",
        CustomerId = Guid.NewGuid(),
        AnnualPremium = 1000m,
        EffectiveDate = new DateOnly(2026, 1, 1),
        ExpiryDate = new DateOnly(2027, 1, 1),
        Status = PolicyStatus.Active,
    };

    [Fact]
    public void FileClaim_OnActivePolicy_CreatesFiledClaim()
    {
        var policy = ActivePolicy();

        var claim = policy.FileClaim("  Hail damage  ", 2500m, Now);

        claim.Status.Should().Be(ClaimStatus.Filed);
        claim.Description.Should().Be("Hail damage");
        claim.PolicyId.Should().Be(policy.Id);
        policy.Claims.Should().ContainSingle();
    }

    [Fact]
    public void FileClaim_OnCancelledPolicy_Throws()
    {
        var policy = ActivePolicy();
        policy.Cancel("non-payment", Now);

        var act = () => policy.FileClaim("x", 100m, Now);

        act.Should().Throw<InvalidStateTransitionException>();
    }

    [Fact]
    public void FileClaim_NonPositiveAmount_Throws()
    {
        var act = () => ActivePolicy().FileClaim("x", 0m, Now);

        act.Should().Throw<DomainException>().WithMessage("*positive*");
    }

    [Fact]
    public void FullLifecycle_Filed_Review_Approve_Pay()
    {
        var claim = ActivePolicy().FileClaim("Collision", 5000m, Now);

        claim.StartReview();
        claim.Status.Should().Be(ClaimStatus.UnderReview);

        claim.Approve(4500m, Now);
        claim.Status.Should().Be(ClaimStatus.Approved);
        claim.ApprovedAmount.Should().Be(4500m);
        claim.ResolvedAtUtc.Should().Be(Now);

        claim.MarkPaid();
        claim.Status.Should().Be(ClaimStatus.Paid);
    }

    [Fact]
    public void Approve_MoreThanClaimed_Throws()
    {
        var claim = ActivePolicy().FileClaim("Collision", 5000m, Now);
        claim.StartReview();

        var act = () => claim.Approve(5000.01m, Now);

        act.Should().Throw<DomainException>().WithMessage("*cannot exceed*");
    }

    [Fact]
    public void Approve_WithoutReview_Throws()
    {
        var claim = ActivePolicy().FileClaim("Collision", 5000m, Now);

        var act = () => claim.Approve(100m, Now);

        act.Should().Throw<InvalidStateTransitionException>();
    }

    [Fact]
    public void Rejected_IsTerminal()
    {
        var claim = ActivePolicy().FileClaim("Collision", 5000m, Now);
        claim.StartReview();
        claim.Reject(Now);

        var act = claim.StartReview;

        act.Should().Throw<InvalidStateTransitionException>();
    }

    [Fact]
    public void Policy_Cancel_RequiresReason_AndOnlyOnce()
    {
        var policy = ActivePolicy();

        var noReason = () => policy.Cancel("  ", Now);
        noReason.Should().Throw<DomainException>();

        policy.Cancel("customer request", Now);
        policy.Status.Should().Be(PolicyStatus.Cancelled);
        policy.CancelledAtUtc.Should().Be(Now);

        var again = () => policy.Cancel("again", Now);
        again.Should().Throw<InvalidStateTransitionException>();
    }

    [Fact]
    public void ApplyRecalculatedPremium_OnlyChangesActivePolicies()
    {
        var policy = ActivePolicy();

        policy.ApplyRecalculatedPremium(1000m).Should().BeFalse("unchanged premium is a no-op");
        policy.ApplyRecalculatedPremium(1100m).Should().BeTrue();
        policy.AnnualPremium.Should().Be(1100m);

        policy.Cancel("done", Now);
        policy.ApplyRecalculatedPremium(1200m).Should().BeFalse("cancelled policies are never re-rated");
    }
}
