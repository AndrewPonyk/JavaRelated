using System.Globalization;
using Grpc.Core;
using Policy.Domain.Exceptions;
using Rating.Grpc;

namespace Policy.Api.Rating;

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

        PremiumResponse response;
        try
        {
            response = await client.CalculatePremiumAsync(
                request, deadline: DateTime.UtcNow.AddSeconds(5), cancellationToken: ct);
        }
        catch (RpcException ex) when (ex.StatusCode == StatusCode.InvalidArgument)
        {
            // The rating engine rejected the request (e.g. unknown product) — a business error, not an outage.
            throw new DomainException(ex.Status.Detail);
        }
        catch (RpcException ex)
        {
            throw new RatingUnavailableException($"Rating service call failed: {ex.StatusCode}", ex);
        }

        var premium = decimal.Parse(response.AnnualPremium, NumberStyles.Number, CultureInfo.InvariantCulture);
        return new RatingOutcome(premium, response.RateTableVersion, [.. response.AppliedSurcharges]);
    }
}

/// <summary>
/// Registered when Rating:GrpcAddress is not configured. Fails fast with a clear
/// message instead of returning fabricated premiums — quotes must never be issued
/// off an unrated price.
/// </summary>
public class UnconfiguredRatingClient : IRatingClient
{
    public Task<RatingOutcome> RateAsync(
        string productCode, string stateCode, IReadOnlyDictionary<string, string> riskFactors, CancellationToken ct)
        => throw new RatingUnavailableException(
            "Rating:GrpcAddress is not configured. Start Rating.Grpc and set the address (see .env.example).");
}
