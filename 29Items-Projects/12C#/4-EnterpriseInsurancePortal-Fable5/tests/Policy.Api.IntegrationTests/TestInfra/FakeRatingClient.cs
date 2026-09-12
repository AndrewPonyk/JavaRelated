using Policy.Api.Rating;
using Policy.Domain.Exceptions;

namespace Policy.Api.IntegrationTests.TestInfra;

/// <summary>
/// Deterministic stand-in for Rating.Grpc with the same error contract as the real
/// client: unknown products surface as DomainException (HTTP 422 at the boundary).
/// </summary>
public class FakeRatingClient : IRatingClient
{
    public const decimal BasePremium = 1000m;
    public const decimal PerRiskFactor = 100m;
    public const string RateTableVersion = "it-test-v1";

    public Task<RatingOutcome> RateAsync(
        string productCode,
        string stateCode,
        IReadOnlyDictionary<string, string> riskFactors,
        CancellationToken ct)
    {
        if (productCode.StartsWith("BAD", StringComparison.OrdinalIgnoreCase))
        {
            throw new DomainException($"Unknown product code '{productCode}'.");
        }

        var premium = BasePremium + riskFactors.Count * PerRiskFactor;
        return Task.FromResult(new RatingOutcome(premium, RateTableVersion, []));
    }
}
