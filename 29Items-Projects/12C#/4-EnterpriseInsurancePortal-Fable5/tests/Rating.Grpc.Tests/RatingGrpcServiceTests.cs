using FluentAssertions;
using Grpc.Core;
using Microsoft.Extensions.Logging.Abstractions;
using Rating.Grpc.Engine;
using Rating.Grpc.Services;
using Xunit;

namespace Rating.Grpc.Tests;

public class RatingGrpcServiceTests
{
    private readonly RatingGrpcService _service = new(
        new RatingEngine(), NullLogger<RatingGrpcService>.Instance);

    // CalculatePremium never touches ServerCallContext, so tests pass null.
    private Task<PremiumResponse> CallAsync(PremiumRequest request)
        => _service.CalculatePremium(request, null!);

    [Fact]
    public async Task ValidRequest_ReturnsInvariantDecimalString_AndSurcharges()
    {
        var request = new PremiumRequest { ProductCode = "AUTO-STD", StateCode = "CA" };
        request.RiskFactors["driverAge"] = "22";

        var response = await CallAsync(request);

        response.AnnualPremium.Should().Be("1625.00"); // 1000 × 1.25 × 1.30
        response.RateTableVersion.Should().Be(RatingEngine.CurrentRateTableVersion);
        response.AppliedSurcharges.Should().ContainSingle().Which.Should().Be("young-driver");
    }

    [Theory]
    [InlineData("", "CA")]
    [InlineData("AUTO-STD", "")]
    public async Task MissingRequiredFields_ThrowInvalidArgument(string product, string state)
    {
        var act = () => CallAsync(new PremiumRequest { ProductCode = product, StateCode = state });

        (await act.Should().ThrowAsync<RpcException>())
            .Which.StatusCode.Should().Be(StatusCode.InvalidArgument);
    }

    [Fact]
    public async Task UnknownProduct_ThrowsInvalidArgument_WithProductInMessage()
    {
        var act = () => CallAsync(new PremiumRequest { ProductCode = "BOAT-STD", StateCode = "CA" });

        var ex = (await act.Should().ThrowAsync<RpcException>()).Which;
        ex.StatusCode.Should().Be(StatusCode.InvalidArgument);
        ex.Status.Detail.Should().Contain("BOAT-STD");
    }
}
