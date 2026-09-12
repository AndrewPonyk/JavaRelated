using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Policy.Api.IntegrationTests.TestInfra;
using Policy.Api.Models;
using Xunit;

namespace Policy.Api.IntegrationTests;

public class ClaimsEndpointTests(PortalApiFactory factory) : IClassFixture<PortalApiFactory>
{
    private readonly HttpClient _broker = factory.CreateBrokerClient();

    private async Task<(CustomerDto Customer, PolicyDto Policy)> BindPolicyAsync()
    {
        var customerResponse = await _broker.PostAsJsonAsync("api/v1/customers", new
        {
            firstName = "Grace",
            lastName = "Hopper",
            email = $"{Guid.NewGuid():N}@example.com",
            dateOfBirth = "1980-12-09",
        });
        var customer = (await customerResponse.Content.ReadFromJsonAsync<CustomerDto>())!;

        var quoteResponse = await _broker.PostAsJsonAsync("api/v1/quotes", new
        {
            customerId = customer.Id,
            brokerId = "BRK-002",
            productCode = "HOME-STD",
            stateCode = "WA",
        });
        var quote = (await quoteResponse.Content.ReadFromJsonAsync<QuoteDto>())!;

        var bindResponse = await _broker.PostAsJsonAsync("api/v1/policies",
            new { quoteId = quote.Id, brokerId = "BRK-002" });
        var policy = (await bindResponse.Content.ReadFromJsonAsync<PolicyDto>())!;
        return (customer, policy);
    }

    [Fact]
    public async Task Customer_CanFileClaim_OnOwnPolicy_ButNotOnForeignPolicy()
    {
        var (customer, policy) = await BindPolicyAsync();
        var (_, foreignPolicy) = await BindPolicyAsync();
        var customerClient = factory.CreateCustomerClient(customer.Id);

        var own = await customerClient.PostAsJsonAsync($"api/v1/policies/{policy.Id}/claims",
            new { description = "Burst pipe in the kitchen", claimedAmount = 3200m });
        own.StatusCode.Should().Be(HttpStatusCode.Created);
        var claim = (await own.Content.ReadFromJsonAsync<ClaimDto>())!;
        claim.Status.Should().Be("Filed");
        claim.ClaimedAmount.Should().Be(3200m);

        var foreign = await customerClient.PostAsJsonAsync($"api/v1/policies/{foreignPolicy.Id}/claims",
            new { description = "Not my policy", claimedAmount = 100m });
        foreign.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task FileClaim_InvalidBody_Returns400()
    {
        var (_, policy) = await BindPolicyAsync();

        var response = await _broker.PostAsJsonAsync($"api/v1/policies/{policy.Id}/claims",
            new { description = "", claimedAmount = -5m });

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Adjudication_FullLifecycle_Review_Approve_Pay()
    {
        var (_, policy) = await BindPolicyAsync();
        var filed = await _broker.PostAsJsonAsync($"api/v1/policies/{policy.Id}/claims",
            new { description = "Storm damage to roof", claimedAmount = 8000m });
        var claim = (await filed.Content.ReadFromJsonAsync<ClaimDto>())!;

        var review = await _broker.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status", new { action = "review" });
        (await review.Content.ReadFromJsonAsync<ClaimDto>())!.Status.Should().Be("UnderReview");

        // Approving more than was claimed violates a business rule.
        var tooMuch = await _broker.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status",
            new { action = "approve", approvedAmount = 9000m });
        tooMuch.StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);

        var approve = await _broker.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status",
            new { action = "approve", approvedAmount = 7500m });
        var approved = (await approve.Content.ReadFromJsonAsync<ClaimDto>())!;
        approved.Status.Should().Be("Approved");
        approved.ApprovedAmount.Should().Be(7500m);

        var pay = await _broker.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status", new { action = "pay" });
        (await pay.Content.ReadFromJsonAsync<ClaimDto>())!.Status.Should().Be("Paid");

        // The policy detail shows the resolved claim.
        var detail = await _broker.GetFromJsonAsync<PolicyDetailDto>($"api/v1/policies/{policy.Id}");
        detail!.Claims.Should().ContainSingle(c => c.Status == "Paid" && c.ApprovedAmount == 7500m);
    }

    [Fact]
    public async Task Adjudication_AsCustomer_IsForbidden()
    {
        var (customer, policy) = await BindPolicyAsync();
        var customerClient = factory.CreateCustomerClient(customer.Id);
        var filed = await customerClient.PostAsJsonAsync($"api/v1/policies/{policy.Id}/claims",
            new { description = "Hail damage", claimedAmount = 900m });
        var claim = (await filed.Content.ReadFromJsonAsync<ClaimDto>())!;

        var response = await customerClient.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status",
            new { action = "approve", approvedAmount = 900m });

        response.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task Approve_BeforeReview_Returns422()
    {
        var (_, policy) = await BindPolicyAsync();
        var filed = await _broker.PostAsJsonAsync($"api/v1/policies/{policy.Id}/claims",
            new { description = "Theft", claimedAmount = 1500m });
        var claim = (await filed.Content.ReadFromJsonAsync<ClaimDto>())!;

        var response = await _broker.PostAsJsonAsync($"api/v1/claims/{claim.Id}/status",
            new { action = "approve", approvedAmount = 1000m });

        response.StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);
    }
}
