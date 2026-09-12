using System.Security.Claims;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Policy.Api.Auth;
using Policy.Api.Rating;
using Xunit;

namespace Policy.Api.Tests;

public class HttpCurrentUserTests
{
    private static HttpCurrentUser UserWith(params Claim[] claims)
    {
        var context = new DefaultHttpContext
        {
            User = new ClaimsPrincipal(new ClaimsIdentity(claims, authenticationType: "Test")),
        };
        return new HttpCurrentUser(new HttpContextAccessor { HttpContext = context });
    }

    [Fact]
    public void Customer_WithCustomerIdClaim_IsScoped()
    {
        var id = Guid.NewGuid();
        var user = UserWith(
            new Claim(ClaimTypes.Name, "jane"),
            new Claim(ClaimTypes.Role, PortalRoles.Customer),
            new Claim(PortalClaimTypes.CustomerId, id.ToString()));

        user.Name.Should().Be("jane");
        user.IsCustomer.Should().BeTrue();
        user.CustomerId.Should().Be(id);
    }

    [Fact]
    public void Broker_IsNeverTreatedAsCustomer_EvenWithBothRoles()
    {
        var user = UserWith(
            new Claim(ClaimTypes.Name, "dual"),
            new Claim(ClaimTypes.Role, PortalRoles.Customer),
            new Claim(ClaimTypes.Role, PortalRoles.Broker));

        user.IsCustomer.Should().BeFalse();
    }

    [Fact]
    public void MissingOrMalformedCustomerId_YieldsNull()
    {
        UserWith(new Claim(ClaimTypes.Role, PortalRoles.Customer)).CustomerId.Should().BeNull();
        UserWith(new Claim(PortalClaimTypes.CustomerId, "not-a-guid")).CustomerId.Should().BeNull();
    }

    [Fact]
    public void NoHttpContext_FallsBackToAnonymous()
    {
        var user = new HttpCurrentUser(new HttpContextAccessor());

        user.Name.Should().Be("anonymous");
        user.IsCustomer.Should().BeFalse();
        user.CustomerId.Should().BeNull();
    }
}

public class UnconfiguredRatingClientTests
{
    [Fact]
    public async Task RateAsync_FailsFast_InsteadOfFabricatingPremiums()
    {
        var client = new UnconfiguredRatingClient();

        var act = () => client.RateAsync("AUTO-STD", "CA", new Dictionary<string, string>(), CancellationToken.None);

        (await act.Should().ThrowAsync<RatingUnavailableException>())
            .Which.Message.Should().Contain("Rating:GrpcAddress");
    }
}

public class PagingTests
{
    [Theory]
    [InlineData(0, 0, 1, 1)]       // floor-clamped
    [InlineData(-5, -10, 1, 1)]    // negatives clamped
    [InlineData(3, 50, 3, 50)]     // passthrough
    [InlineData(1, 10_000, 1, 200)] // ceiling-clamped to MaxPageSize
    public void Normalize_ClampsInsteadOfRejecting(int page, int size, int expectedPage, int expectedSize)
    {
        var paging = Policy.Api.Models.Paging.Normalize(page, size);

        paging.Page.Should().Be(expectedPage);
        paging.PageSize.Should().Be(expectedSize);
        paging.Skip.Should().Be((expectedPage - 1) * expectedSize);
    }
}
