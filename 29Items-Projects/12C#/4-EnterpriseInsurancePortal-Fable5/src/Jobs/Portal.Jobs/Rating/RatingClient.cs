using System.Globalization;
using Rating.Grpc;

namespace Portal.Jobs.Rating;

public record RatingOutcome(decimal AnnualPremium, string RateTableVersion);

/// <summary>
/// Job-side seam over Rating.Grpc. Unlike the API's client it performs no
/// business-error mapping: any failure propagates so Hangfire's retry policy
/// re-runs the (idempotent) job.
/// </summary>
public interface IRatingClient
{
    Task<RatingOutcome> RateAsync(
        string productCode,
        string stateCode,
        IReadOnlyDictionary<string, string> riskFactors,
        CancellationToken ct);
}

public class GrpcRatingClient(RatingService.RatingServiceClient client) : IRatingClient
{
    public async Task<RatingOutcome> RateAsync(
        string productCode,
        string stateCode,
        IReadOnlyDictionary<string, string> riskFactors,
        CancellationToken ct)
    {
        var request = new PremiumRequest
        {
            ProductCode = productCode,
            StateCode = stateCode,
        };
        foreach (var (key, value) in riskFactors)
        {
            request.RiskFactors[key] = value;
        }

        var response = await client.CalculatePremiumAsync(
            request, deadline: DateTime.UtcNow.AddSeconds(10), cancellationToken: ct);

        return new RatingOutcome(
            decimal.Parse(response.AnnualPremium, NumberStyles.Number, CultureInfo.InvariantCulture),
            response.RateTableVersion);
    }
}
