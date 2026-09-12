using FluentAssertions;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.ValueObjects;
using Xunit;

namespace InvoiceFactoring.Domain.UnitTests;

public class CreditAssessmentTests
{
    [Theory]
    [InlineData(0.01, RiskGrade.A)]
    [InlineData(0.05, RiskGrade.B)]
    [InlineData(0.12, RiskGrade.C)]
    [InlineData(0.25, RiskGrade.D)]
    [InlineData(0.50, RiskGrade.F)]
    public void FromScore_MapsProbabilityToExpectedGrade(double pd, RiskGrade expected)
    {
        var assessment = CreditAssessment.FromScore(Guid.NewGuid(), pd, "test", "[]");

        assessment.RiskGrade.Should().Be(expected);
    }

    [Fact]
    public void FromScore_AboveThreshold_IsNotApproved()
    {
        var assessment = CreditAssessment.FromScore(Guid.NewGuid(), 0.4, "test", "[]", declineThreshold: 0.30);

        assessment.IsApproved.Should().BeFalse();
        assessment.RiskGrade.Should().Be(RiskGrade.F);
    }

    [Fact]
    public void NetDisbursement_IsGrossAdvanceMinusFee()
    {
        // Grade B: advance rate 0.85, fee rate 0.025 on a 10,000 invoice.
        var assessment = CreditAssessment.FromScore(Guid.NewGuid(), 0.05, "test", "[]");
        var face = new Money(10_000m);

        assessment.GrossAdvance(face).Amount.Should().Be(8_500m);
        assessment.Fee(face).Amount.Should().Be(250m);
        assessment.NetDisbursement(face).Amount.Should().Be(8_250m);
    }
}
