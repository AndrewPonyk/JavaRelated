using FluentAssertions;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Xunit;

namespace Policy.Api.Tests;

public class QuoteBindingTests
{
    private static readonly DateTime Now = new(2026, 6, 12, 10, 0, 0, DateTimeKind.Utc);

    private static Quote IssuedQuote() => Quote.Issue(
        customerId: Guid.NewGuid(),
        brokerId: "BRK-001",
        productCode: "AUTO-STD",
        stateCode: "ca",
        riskFactorsJson: """{"driverAge":"30"}""",
        premium: 1200m,
        rateTableVersion: "2026.06",
        nowUtc: Now);

    [Fact]
    public void Issue_NormalizesStateCode_AndSetsThirtyDayValidity()
    {
        var quote = IssuedQuote();

        quote.StateCode.Should().Be("CA");
        quote.Status.Should().Be(QuoteStatus.Issued);
        quote.ExpiresAtUtc.Should().Be(Now.AddDays(30));
    }

    [Fact]
    public void Issue_NonPositivePremium_Throws()
    {
        var act = () => Quote.Issue(Guid.NewGuid(), "BRK-001", "AUTO-STD", "CA", "{}", 0m, "v", Now);

        act.Should().Throw<DomainException>().WithMessage("*positive*");
    }

    [Fact]
    public void Bind_IssuedQuote_CreatesActivePolicyAndMarksQuoteBound()
    {
        var quote = IssuedQuote();

        var policy = quote.Bind(Now);

        policy.Status.Should().Be(PolicyStatus.Active);
        policy.AnnualPremium.Should().Be(1200m);
        policy.QuoteId.Should().Be(quote.Id);
        policy.CustomerId.Should().Be(quote.CustomerId);
        policy.PolicyNumber.Should().StartWith("POL-20260612-");
        policy.EffectiveDate.Should().Be(new DateOnly(2026, 6, 12));
        policy.ExpiryDate.Should().Be(new DateOnly(2027, 6, 12));
        quote.Status.Should().Be(QuoteStatus.Bound);
    }

    [Fact]
    public void Bind_ExpiredQuote_ThrowsAndMarksQuoteExpired()
    {
        var quote = IssuedQuote();

        var act = () => quote.Bind(Now.AddDays(31));

        act.Should().Throw<InvalidStateTransitionException>().WithMessage("*expired*");
        quote.Status.Should().Be(QuoteStatus.Expired);
    }

    [Theory]
    [InlineData(QuoteStatus.Draft)]
    [InlineData(QuoteStatus.Bound)]
    [InlineData(QuoteStatus.Declined)]
    [InlineData(QuoteStatus.Expired)]
    public void Bind_NonIssuedQuote_Throws(QuoteStatus status)
    {
        var quote = IssuedQuote();
        quote.Status = status;

        var act = () => quote.Bind(Now);

        act.Should().Throw<InvalidStateTransitionException>();
    }

    [Fact]
    public void Decline_IssuedQuote_Succeeds_ButBoundQuote_Throws()
    {
        var quote = IssuedQuote();
        quote.Decline();
        quote.Status.Should().Be(QuoteStatus.Declined);

        var bound = IssuedQuote();
        bound.Bind(Now);
        var act = () => bound.Decline();
        act.Should().Throw<InvalidStateTransitionException>();
    }
}
