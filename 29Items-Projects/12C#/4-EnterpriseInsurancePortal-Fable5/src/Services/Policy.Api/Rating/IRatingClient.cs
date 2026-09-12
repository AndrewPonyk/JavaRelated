namespace Policy.Api.Rating;

public record RatingOutcome(decimal AnnualPremium, string RateTableVersion, IReadOnlyList<string> AppliedSurcharges);

/// <summary>Seam over the Rating.Grpc service so application code never touches gRPC types.</summary>
public interface IRatingClient
{
    Task<RatingOutcome> RateAsync(
        string productCode,
        string stateCode,
        IReadOnlyDictionary<string, string> riskFactors,
        CancellationToken ct);
}

/// <summary>The rating engine could not be reached. Mapped to HTTP 503 by the middleware.</summary>
public class RatingUnavailableException(string message, Exception? inner = null) : Exception(message, inner);
