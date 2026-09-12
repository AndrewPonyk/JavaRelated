using System.Globalization;
using Grpc.Core;
using Rating.Grpc.Engine;

namespace Rating.Grpc.Services;

public class RatingGrpcService(RatingEngine engine, ILogger<RatingGrpcService> logger)
    : RatingService.RatingServiceBase
{
    public override Task<PremiumResponse> CalculatePremium(PremiumRequest request, ServerCallContext context)
    {
        if (string.IsNullOrWhiteSpace(request.ProductCode))
        {
            throw new RpcException(new Status(StatusCode.InvalidArgument, "product_code is required."));
        }

        if (string.IsNullOrWhiteSpace(request.StateCode))
        {
            throw new RpcException(new Status(StatusCode.InvalidArgument, "state_code is required."));
        }

        RatingResult result;
        try
        {
            result = engine.Calculate(request.ProductCode, request.StateCode, request.RiskFactors);
        }
        catch (UnknownProductException ex)
        {
            throw new RpcException(new Status(StatusCode.InvalidArgument, ex.Message));
        }

        logger.LogInformation(
            "Rated product {Product} in {State}: {Premium} (surcharges: {Surcharges})",
            request.ProductCode, request.StateCode, result.AnnualPremium,
            result.AppliedSurcharges.Count == 0 ? "none" : string.Join(", ", result.AppliedSurcharges));

        var response = new PremiumResponse
        {
            // Decimal as invariant string — proto3 has no decimal type and money must not be a double.
            AnnualPremium = result.AnnualPremium.ToString("0.00", CultureInfo.InvariantCulture),
            RateTableVersion = result.RateTableVersion,
        };
        response.AppliedSurcharges.AddRange(result.AppliedSurcharges);
        return Task.FromResult(response);
    }
}
