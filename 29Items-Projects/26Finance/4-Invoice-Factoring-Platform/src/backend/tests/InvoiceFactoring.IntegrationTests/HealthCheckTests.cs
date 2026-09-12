using System.Net;
using FluentAssertions;
using Xunit;

namespace InvoiceFactoring.IntegrationTests;

/// <summary>
/// Smoke test that the host boots and the liveness probe responds. A template for
/// fuller endpoint tests (submit invoice → poll for offer) once test auth is wired up.
/// </summary>
public sealed class HealthCheckTests : IClassFixture<CustomWebApplicationFactory>
{
    private readonly CustomWebApplicationFactory _factory;

    public HealthCheckTests(CustomWebApplicationFactory factory) => _factory = factory;

    [Fact]
    public async Task LivenessProbe_ReturnsHealthy()
    {
        var client = _factory.CreateClient();

        var response = await client.GetAsync("/health/live");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }
}
