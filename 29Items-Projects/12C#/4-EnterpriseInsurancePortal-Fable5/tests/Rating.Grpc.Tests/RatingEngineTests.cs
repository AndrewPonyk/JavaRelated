using FluentAssertions;
using Rating.Grpc.Engine;
using Xunit;

namespace Rating.Grpc.Tests;

public class RatingEngineTests
{
    private readonly RatingEngine _engine = new();

    private static Dictionary<string, string> Factors(params (string Key, string Value)[] pairs)
        => pairs.ToDictionary(p => p.Key, p => p.Value);

    [Fact]
    public void BaseRate_TimesStateFactor_NoSurcharges()
    {
        var result = _engine.Calculate("AUTO-STD", "CA", Factors());

        result.AnnualPremium.Should().Be(1250.00m); // 1000 × 1.25
        result.AppliedSurcharges.Should().BeEmpty();
        result.RateTableVersion.Should().Be(RatingEngine.CurrentRateTableVersion);
    }

    [Fact]
    public void UnknownState_UsesNeutralFactor()
    {
        var result = _engine.Calculate("HOME-STD", "MT", Factors());

        result.AnnualPremium.Should().Be(800.00m);
    }

    [Fact]
    public void ProductCode_IsCaseInsensitive()
    {
        _engine.Calculate("auto-std", "CA", Factors()).AnnualPremium
            .Should().Be(_engine.Calculate("AUTO-STD", "CA", Factors()).AnnualPremium);
    }

    [Fact]
    public void UnknownProduct_Throws()
    {
        var act = () => _engine.Calculate("BOAT-STD", "CA", Factors());

        act.Should().Throw<UnknownProductException>().WithMessage("*BOAT-STD*");
    }

    [Theory]
    [InlineData("22", 1300.00, "young-driver")] // 1000 × 1.30
    [InlineData("75", 1200.00, "senior-driver")] // 1000 × 1.20
    [InlineData("40", 1000.00, null)] // no age surcharge
    public void DriverAge_Surcharges(string age, decimal expected, string? surcharge)
    {
        var result = _engine.Calculate("AUTO-STD", "MT", Factors(("driverAge", age)));

        result.AnnualPremium.Should().Be(expected);
        if (surcharge is null)
        {
            result.AppliedSurcharges.Should().BeEmpty();
        }
        else
        {
            result.AppliedSurcharges.Should().ContainSingle().Which.Should().Be(surcharge);
        }
    }

    [Theory]
    [InlineData("1", 1100.00)] // +10%
    [InlineData("3", 1300.00)] // +30%
    [InlineData("9", 1500.00)] // capped at +50%
    public void PriorClaims_LoadingIsCappedAtFiftyPercent(string priorClaims, decimal expected)
    {
        var result = _engine.Calculate("AUTO-STD", "MT", Factors(("priorClaims", priorClaims)));

        result.AnnualPremium.Should().Be(expected);
        result.AppliedSurcharges.Should().Contain("prior-claims");
    }

    [Fact]
    public void HighValueVehicle_AddsPercentageAboveThreshold()
    {
        var result = _engine.Calculate("AUTO-STD", "MT", Factors(("vehicleValue", "50000")));

        // 1000 + (50000 − 30000) × 1.5% = 1300
        result.AnnualPremium.Should().Be(1300.00m);
        result.AppliedSurcharges.Should().Contain("high-value-vehicle");
    }

    [Fact]
    public void Smoker_LoadsLifePremium()
    {
        var result = _engine.Calculate("LIFE-TERM", "MT", Factors(("smoker", "true")));

        result.AnnualPremium.Should().Be(625.00m); // 500 × 1.25
        result.AppliedSurcharges.Should().Contain("smoker");
    }

    [Fact]
    public void CombinedFactors_MultiplyThenAdd_Deterministically()
    {
        var factors = Factors(("driverAge", "22"), ("priorClaims", "2"), ("vehicleValue", "40000"));

        var result = _engine.Calculate("AUTO-STD", "CA", factors);

        // 1000 × 1.25 (CA) × 1.30 (young) × 1.20 (2 claims) + 10000 × 0.015 = 1950 + 150
        result.AnnualPremium.Should().Be(2100.00m);
        result.AppliedSurcharges.Should().BeEquivalentTo(["young-driver", "prior-claims", "high-value-vehicle"]);

        // Determinism — same input, same output (nightly job relies on this).
        _engine.Calculate("AUTO-STD", "CA", factors).AnnualPremium.Should().Be(result.AnnualPremium);
    }

    [Fact]
    public void UnknownRiskFactors_AreIgnored_ForForwardCompatibility()
    {
        var result = _engine.Calculate("AUTO-STD", "MT", Factors(("futureFactor", "42")));

        result.AnnualPremium.Should().Be(1000.00m);
        result.AppliedSurcharges.Should().BeEmpty();
    }

    [Fact]
    public void MalformedNumericFactors_AreIgnored_NotErrors()
    {
        var result = _engine.Calculate("AUTO-STD", "MT", Factors(("driverAge", "not-a-number")));

        result.AnnualPremium.Should().Be(1000.00m);
    }
}
