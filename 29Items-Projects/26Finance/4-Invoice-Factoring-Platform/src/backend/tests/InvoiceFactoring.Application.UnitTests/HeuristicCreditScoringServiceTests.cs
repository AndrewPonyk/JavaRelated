using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Scoring;
using InvoiceFactoring.Infrastructure.Scoring;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class HeuristicCreditScoringServiceTests
{
    private readonly HeuristicCreditScoringService _scorer = new();

    private static CreditScoringRequest HealthyBorrower() => new(
        InvoiceAmount: 10_000m,
        PaymentTermDays: 30,
        DaysUntilDue: 30,
        DebtorPriorInvoicesPaid: 50,
        DebtorPriorInvoicesDefaulted: 0,
        BorrowerMonthlyInflow: 200_000,
        BorrowerMonthlyOutflow: 100_000,
        BorrowerAverageDailyBalance: 80_000,
        BorrowerTenureMonths: 60,
        IndustryCode: "TECH");

    private static CreditScoringRequest RiskyBorrower() => new(
        InvoiceAmount: 250_000m,
        PaymentTermDays: 90,
        DaysUntilDue: 90,
        DebtorPriorInvoicesPaid: 2,
        DebtorPriorInvoicesDefaulted: 8,
        BorrowerMonthlyInflow: 0,
        BorrowerMonthlyOutflow: 0,
        BorrowerAverageDailyBalance: 0,
        BorrowerTenureMonths: 0,
        IndustryCode: "UNKNOWN");

    [Fact]
    public async Task Score_HealthyBorrower_ProducesLowPd()
    {
        var result = await _scorer.ScoreAsync(HealthyBorrower());

        result.ProbabilityOfDefault.Should().BeLessThan(0.15);
        result.ModelVersion.Should().Be(HeuristicCreditScoringService.ModelVersion);
        result.ReasonCodes.Should().NotBeEmpty();
    }

    [Fact]
    public async Task Score_RiskyBorrower_ProducesHighPd_DrivenByDefaultHistory()
    {
        var result = await _scorer.ScoreAsync(RiskyBorrower());

        result.ProbabilityOfDefault.Should().BeGreaterThan(0.30);
        result.ReasonCodes.First().Feature.Should().Be("DebtorDefaultHistory");
    }

    [Fact]
    public async Task Score_IsDeterministic()
    {
        var a = await _scorer.ScoreAsync(HealthyBorrower());
        var b = await _scorer.ScoreAsync(HealthyBorrower());

        b.ProbabilityOfDefault.Should().Be(a.ProbabilityOfDefault);
    }

    [Fact]
    public async Task Score_PdIsAlwaysAProbability()
    {
        foreach (var request in new[] { HealthyBorrower(), RiskyBorrower() })
        {
            var result = await _scorer.ScoreAsync(request);
            result.ProbabilityOfDefault.Should().BeInRange(0.0, 1.0);
        }
    }
}
