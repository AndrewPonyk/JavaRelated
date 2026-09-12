using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Microsoft.Extensions.DependencyInjection;
using Policy.Api.IntegrationTests.TestInfra;
using Policy.Api.Models;
using Policy.Infrastructure.Outbox;
using Xunit;

namespace Policy.Api.IntegrationTests;

public class PoliciesEndpointTests(PortalApiFactory factory) : IClassFixture<PortalApiFactory>
{
    private readonly HttpClient _broker = factory.CreateBrokerClient();

    private async Task<CustomerDto> CreateCustomerAsync(string email)
    {
        var response = await _broker.PostAsJsonAsync("api/v1/customers",
            new { firstName = "Ada", lastName = "Lovelace", email, dateOfBirth = "1990-12-10" });
        response.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await response.Content.ReadFromJsonAsync<CustomerDto>())!;
    }

    private async Task<QuoteDto> CreateQuoteAsync(Guid customerId)
    {
        var response = await _broker.PostAsJsonAsync("api/v1/quotes", new
        {
            customerId,
            brokerId = "BRK-001",
            productCode = "AUTO-STD",
            stateCode = "CA",
            riskFactors = new Dictionary<string, string> { ["driverAge"] = "30" },
        });
        response.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await response.Content.ReadFromJsonAsync<QuoteDto>())!;
    }

    [Fact]
    public async Task QuoteToBind_HappyPath_PersistsPolicyAndEmitsOutboxEvent()
    {
        var customer = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var quote = await CreateQuoteAsync(customer.Id);

        // Premium comes from the rating engine: 1000 base + 1 risk factor × 100.
        quote.Premium.Should().Be(1100m);
        quote.RateTableVersion.Should().Be(FakeRatingClient.RateTableVersion);
        quote.Status.Should().Be("Issued");

        var bindResponse = await _broker.PostAsJsonAsync("api/v1/policies",
            new { quoteId = quote.Id, brokerId = "BRK-001" });
        bindResponse.StatusCode.Should().Be(HttpStatusCode.Created);
        var policy = (await bindResponse.Content.ReadFromJsonAsync<PolicyDto>())!;
        policy.AnnualPremium.Should().Be(1100m);
        policy.Status.Should().Be("Active");

        // The policy is retrievable with full detail.
        var detail = await _broker.GetFromJsonAsync<PolicyDetailDto>($"api/v1/policies/{policy.Id}");
        detail!.PolicyNumber.Should().Be(policy.PolicyNumber);
        detail.CustomerName.Should().Be("Ada Lovelace");

        // Drive the outbox relay one cycle and verify the event reached the (recorded) broker.
        using var scope = factory.Services.CreateScope();
        var processor = scope.ServiceProvider.GetRequiredService<OutboxProcessor>();
        var published = await processor.ProcessPendingAsync(CancellationToken.None);
        published.Should().BeGreaterThan(0);

        factory.PublishedEvents.Events.Should().Contain(e =>
            e.EventType == "PolicyBoundEvent"
            && e.Key == policy.Id.ToString()
            && e.Topic == "policy-events"
            && e.Payload.Contains(policy.PolicyNumber));

        // Relay is idempotent: a second cycle finds nothing new for this policy.
        var again = await processor.ProcessPendingAsync(CancellationToken.None);
        again.Should().Be(0);
    }

    [Fact]
    public async Task BindPolicy_InvalidRequest_Returns400WithValidationDetails()
    {
        var response = await _broker.PostAsJsonAsync("api/v1/policies",
            new { quoteId = Guid.Empty, brokerId = "" });

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        var body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("BrokerId").And.Contain("QuoteId");
    }

    [Fact]
    public async Task BindPolicy_SameQuoteTwice_Returns422ProblemDetails()
    {
        var customer = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var quote = await CreateQuoteAsync(customer.Id);
        (await _broker.PostAsJsonAsync("api/v1/policies", new { quoteId = quote.Id, brokerId = "BRK-001" }))
            .StatusCode.Should().Be(HttpStatusCode.Created);

        var second = await _broker.PostAsJsonAsync("api/v1/policies",
            new { quoteId = quote.Id, brokerId = "BRK-001" });

        second.StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);
        (await second.Content.ReadAsStringAsync()).Should().Contain("Only issued quotes can be bound");
    }

    [Fact]
    public async Task BindPolicy_UnknownQuote_Returns404()
    {
        var response = await _broker.PostAsJsonAsync("api/v1/policies",
            new { quoteId = Guid.NewGuid(), brokerId = "BRK-001" });

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task CreateQuote_UnknownProduct_Returns422FromRatingEngine()
    {
        var customer = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");

        var response = await _broker.PostAsJsonAsync("api/v1/quotes", new
        {
            customerId = customer.Id,
            brokerId = "BRK-001",
            productCode = "BAD-PROD",
            stateCode = "CA",
        });

        response.StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);
        (await response.Content.ReadAsStringAsync()).Should().Contain("BAD-PROD");
    }

    [Fact]
    public async Task GetPolicies_AsCustomer_ReturnsOnlyOwnPolicies()
    {
        var customerA = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var customerB = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var quoteA = await CreateQuoteAsync(customerA.Id);
        var quoteB = await CreateQuoteAsync(customerB.Id);
        await _broker.PostAsJsonAsync("api/v1/policies", new { quoteId = quoteA.Id, brokerId = "BRK-001" });
        await _broker.PostAsJsonAsync("api/v1/policies", new { quoteId = quoteB.Id, brokerId = "BRK-001" });

        var customerClient = factory.CreateCustomerClient(customerA.Id);

        // Even with an explicit filter for B, customer A only sees their own policies.
        var visible = await customerClient.GetFromJsonAsync<List<PolicyDto>>(
            $"api/v1/policies?customerId={customerB.Id}");
        visible.Should().NotBeEmpty();
        visible.Should().OnlyContain(p => p.CustomerId == customerA.Id);

        // And B's policy detail is a 404 for A, indistinguishable from "missing".
        var policiesOfB = await _broker.GetFromJsonAsync<List<PolicyDto>>(
            $"api/v1/policies?customerId={customerB.Id}");
        var foreign = await customerClient.GetAsync($"api/v1/policies/{policiesOfB![0].Id}");
        foreign.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task CancelPolicy_AsCustomer_IsForbidden()
    {
        var customer = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var quote = await CreateQuoteAsync(customer.Id);
        var bind = await _broker.PostAsJsonAsync("api/v1/policies", new { quoteId = quote.Id, brokerId = "BRK-001" });
        var policy = (await bind.Content.ReadFromJsonAsync<PolicyDto>())!;

        var customerClient = factory.CreateCustomerClient(customer.Id);
        using var request = new HttpRequestMessage(HttpMethod.Delete, $"api/v1/policies/{policy.Id}")
        {
            Content = JsonContent.Create(new { reason = "I changed my mind" }),
        };

        var response = await customerClient.SendAsync(request);

        response.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task CancelPolicy_AsBroker_Succeeds_AndSecondCancelIs422()
    {
        var customer = await CreateCustomerAsync($"{Guid.NewGuid():N}@example.com");
        var quote = await CreateQuoteAsync(customer.Id);
        var bind = await _broker.PostAsJsonAsync("api/v1/policies", new { quoteId = quote.Id, brokerId = "BRK-001" });
        var policy = (await bind.Content.ReadFromJsonAsync<PolicyDto>())!;

        using var first = new HttpRequestMessage(HttpMethod.Delete, $"api/v1/policies/{policy.Id}")
        {
            Content = JsonContent.Create(new { reason = "non-payment" }),
        };
        var firstResponse = await _broker.SendAsync(first);
        firstResponse.StatusCode.Should().Be(HttpStatusCode.OK);
        (await firstResponse.Content.ReadFromJsonAsync<PolicyDto>())!.Status.Should().Be("Cancelled");

        using var second = new HttpRequestMessage(HttpMethod.Delete, $"api/v1/policies/{policy.Id}")
        {
            Content = JsonContent.Create(new { reason = "again" }),
        };
        (await _broker.SendAsync(second)).StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);
    }

    [Fact]
    public async Task CreateCustomer_DuplicateEmail_Returns422()
    {
        var email = $"{Guid.NewGuid():N}@example.com";
        await CreateCustomerAsync(email);

        var response = await _broker.PostAsJsonAsync("api/v1/customers",
            new { firstName = "Dup", lastName = "Licate", email, dateOfBirth = "1990-01-01" });

        response.StatusCode.Should().Be(HttpStatusCode.UnprocessableEntity);
        (await response.Content.ReadAsStringAsync()).Should().Contain("already exists");
    }

    [Fact]
    public async Task HealthEndpoint_Responds()
    {
        var response = await factory.CreateClient().GetAsync("/healthz");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }
}
