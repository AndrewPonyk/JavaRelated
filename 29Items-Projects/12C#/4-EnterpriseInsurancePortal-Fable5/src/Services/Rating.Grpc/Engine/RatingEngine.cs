using System.Globalization;

namespace Rating.Grpc.Engine;

public record RatingResult(decimal AnnualPremium, string RateTableVersion, IReadOnlyList<string> AppliedSurcharges);

public class UnknownProductException(string productCode)
    : Exception($"Unknown product code '{productCode}'.")
{
    public string ProductCode { get; } = productCode;
}

/// <summary>
/// Deterministic premium rating engine: base rate per product × state factor,
/// plus risk-factor surcharges. Pure and side-effect free so it is trivially
/// unit-testable and safe to call from the nightly recalculation job.
/// </summary>
public class RatingEngine
{
    public const string CurrentRateTableVersion = "2026.06";

    private static readonly Dictionary<string, decimal> BaseRates = new(StringComparer.OrdinalIgnoreCase)
    {
        ["AUTO-STD"] = 1000m,
        ["AUTO-PREM"] = 1500m,
        ["HOME-STD"] = 800m,
        ["HOME-PREM"] = 1300m,
        ["LIFE-TERM"] = 500m,
    };

    private static readonly Dictionary<string, decimal> StateFactors = new(StringComparer.OrdinalIgnoreCase)
    {
        ["CA"] = 1.25m,
        ["NY"] = 1.30m,
        ["FL"] = 1.40m,
        ["TX"] = 1.15m,
        ["WA"] = 1.10m,
        // Any other state uses factor 1.00
    };

    public static IReadOnlyCollection<string> SupportedProducts => BaseRates.Keys;

    public RatingResult Calculate(string productCode, string stateCode, IReadOnlyDictionary<string, string> riskFactors)
    {
        if (!BaseRates.TryGetValue(productCode, out var baseRate))
        {
            throw new UnknownProductException(productCode);
        }

        var stateFactor = StateFactors.GetValueOrDefault(stateCode, 1.00m);
        var premium = baseRate * stateFactor;
        var surcharges = new List<string>();

        if (TryGetInt(riskFactors, "driverAge", out var driverAge))
        {
            if (driverAge < 25)
            {
                premium *= 1.30m;
                surcharges.Add("young-driver");
            }
            else if (driverAge > 70)
            {
                premium *= 1.20m;
                surcharges.Add("senior-driver");
            }
        }

        if (TryGetInt(riskFactors, "priorClaims", out var priorClaims) && priorClaims > 0)
        {
            // +10% per prior claim, capped at +50%
            var loading = Math.Min(priorClaims * 0.10m, 0.50m);
            premium *= 1 + loading;
            surcharges.Add("prior-claims");
        }

        if (TryGetDecimal(riskFactors, "vehicleValue", out var vehicleValue) && vehicleValue > 30_000m)
        {
            premium += (vehicleValue - 30_000m) * 0.015m;
            surcharges.Add("high-value-vehicle");
        }

        if (TryGetDecimal(riskFactors, "propertyValue", out var propertyValue) && propertyValue > 250_000m)
        {
            premium += (propertyValue - 250_000m) * 0.002m;
            surcharges.Add("high-value-property");
        }

        if (riskFactors.TryGetValue("smoker", out var smoker)
            && bool.TryParse(smoker, out var isSmoker) && isSmoker)
        {
            premium *= 1.25m;
            surcharges.Add("smoker");
        }

        // Unknown risk-factor keys are deliberately ignored: callers may send factors
        // a future rate table understands without breaking older engines.
        return new RatingResult(Math.Round(premium, 2, MidpointRounding.AwayFromZero),
            CurrentRateTableVersion, surcharges);
    }

    private static bool TryGetInt(IReadOnlyDictionary<string, string> factors, string key, out int value)
    {
        value = 0;
        return factors.TryGetValue(key, out var raw)
            && int.TryParse(raw, NumberStyles.Integer, CultureInfo.InvariantCulture, out value);
    }

    private static bool TryGetDecimal(IReadOnlyDictionary<string, string> factors, string key, out decimal value)
    {
        value = 0;
        return factors.TryGetValue(key, out var raw)
            && decimal.TryParse(raw, NumberStyles.Number, CultureInfo.InvariantCulture, out value);
    }
}
