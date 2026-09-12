using System.Net.Http.Json;
using FluentAssertions;
using Policy.Api.IntegrationTests.TestInfra;
using Policy.Api.Models;
using Xunit;

namespace Policy.Api.IntegrationTests;

public class PaginationTests(PortalApiFactory factory) : IClassFixture<PortalApiFactory>
{
    private readonly HttpClient _broker = factory.CreateBrokerClient();

    [Fact]
    public async Task CustomersList_PagesResults_AndReportsTotalCount()
    {
        for (var i = 0; i < 5; i++)
        {
            var response = await _broker.PostAsJsonAsync("api/v1/customers", new
            {
                firstName = "Page",
                lastName = $"Customer{i:00}",
                email = $"page-{Guid.NewGuid():N}@example.com",
                dateOfBirth = "1990-01-01",
            });
            response.EnsureSuccessStatusCode();
        }

        var page1 = await _broker.GetAsync("api/v1/customers?page=1&pageSize=2");
        var page1Items = (await page1.Content.ReadFromJsonAsync<List<CustomerDto>>())!;
        page1Items.Should().HaveCount(2);

        var total = int.Parse(page1.Headers.GetValues("X-Total-Count").Single());
        total.Should().BeGreaterThanOrEqualTo(5);

        var page2 = await _broker.GetAsync("api/v1/customers?page=2&pageSize=2");
        var page2Items = (await page2.Content.ReadFromJsonAsync<List<CustomerDto>>())!;
        page2Items.Should().HaveCount(2);
        page2Items.Select(c => c.Id).Should().NotIntersectWith(page1Items.Select(c => c.Id));

        // Out-of-range input is clamped, not rejected.
        var clamped = await _broker.GetAsync("api/v1/customers?page=0&pageSize=99999");
        clamped.EnsureSuccessStatusCode();
        (await clamped.Content.ReadFromJsonAsync<List<CustomerDto>>())!
            .Count.Should().BeLessThanOrEqualTo(Paging.MaxPageSize);

        // A page beyond the data is an empty list, not an error.
        var beyond = await _broker.GetAsync("api/v1/customers?page=999&pageSize=50");
        (await beyond.Content.ReadFromJsonAsync<List<CustomerDto>>())!.Should().BeEmpty();
    }
}
