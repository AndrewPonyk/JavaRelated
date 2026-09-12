using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Policy.Api.Auth;
using Policy.Api.Models;
using Policy.Api.Services;
using Policy.Api.Tests.TestInfra;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Xunit;

namespace Policy.Api.Tests;

public class PolicyServiceTests : IDisposable
{
    private readonly SqliteDb _db = new();
    private readonly ICurrentUser _broker = Substitute.For<ICurrentUser>();

    public PolicyServiceTests()
    {
        _broker.Name.Returns("test-broker");
        _broker.IsCustomer.Returns(false);
    }

    private PolicyService CreateService(ICurrentUser? user = null)
        => new(_db, user ?? _broker, NullLogger<PolicyService>.Instance);

    private async Task<(Customer Customer, Quote Quote)> SeedIssuedQuoteAsync()
    {
        await using var db = _db.CreateDbContext();
        var customer = Customer.Create("Ada", "Lovelace", $"{Guid.NewGuid():N}@example.com",
            new DateOnly(1990, 1, 1), new DateOnly(2026, 6, 12));
        var quote = Quote.Issue(customer.Id, "BRK-001", "AUTO-STD", "CA", "{}", 1500m, "2026.06", DateTime.UtcNow);
        db.Customers.Add(customer);
        db.Quotes.Add(quote);
        await db.SaveChangesAsync();
        return (customer, quote);
    }

    [Fact]
    public async Task BindAsync_PersistsPolicy_OutboxEvent_AndAuditRow_Atomically()
    {
        var (_, quote) = await SeedIssuedQuoteAsync();

        var dto = await CreateService().BindAsync(new BindPolicyRequest(quote.Id, "BRK-001"), CancellationToken.None);

        await using var db = _db.CreateDbContext();
        var policy = db.Policies.Single(p => p.Id == dto.Id);
        policy.AnnualPremium.Should().Be(1500m);
        policy.Status.Should().Be(PolicyStatus.Active);

        db.Quotes.Single(q => q.Id == quote.Id).Status.Should().Be(QuoteStatus.Bound);

        var outbox = db.OutboxMessages.Single();
        outbox.EventType.Should().Be("PolicyBoundEvent");
        outbox.Key.Should().Be(policy.Id.ToString());
        outbox.ProcessedAtUtc.Should().BeNull();
        outbox.Payload.Should().Contain(policy.PolicyNumber);

        var audit = db.AuditEntries.Single(a => a.Action == "PolicyBound");
        audit.Actor.Should().Be("test-broker");
        audit.EntityId.Should().Be(policy.Id);
    }

    [Fact]
    public async Task BindAsync_SameQuoteTwice_ThrowsInvalidStateTransition()
    {
        var (_, quote) = await SeedIssuedQuoteAsync();
        var service = CreateService();
        await service.BindAsync(new BindPolicyRequest(quote.Id, "BRK-001"), CancellationToken.None);

        var act = () => service.BindAsync(new BindPolicyRequest(quote.Id, "BRK-001"), CancellationToken.None);

        await act.Should().ThrowAsync<InvalidStateTransitionException>();
    }

    [Fact]
    public async Task BindAsync_UnknownQuote_ThrowsNotFound()
    {
        var act = () => CreateService().BindAsync(new BindPolicyRequest(Guid.NewGuid(), "BRK-001"), CancellationToken.None);

        await act.Should().ThrowAsync<EntityNotFoundException>();
    }

    [Fact]
    public async Task ListAsync_AsCustomer_IsAlwaysScopedToOwnPolicies()
    {
        var (customerA, quoteA) = await SeedIssuedQuoteAsync();
        var (_, quoteB) = await SeedIssuedQuoteAsync();
        var broker = CreateService();
        await broker.BindAsync(new BindPolicyRequest(quoteA.Id, "BRK-001"), CancellationToken.None);
        await broker.BindAsync(new BindPolicyRequest(quoteB.Id, "BRK-001"), CancellationToken.None);

        var customerUser = Substitute.For<ICurrentUser>();
        customerUser.Name.Returns("customer-a");
        customerUser.IsCustomer.Returns(true);
        customerUser.CustomerId.Returns(customerA.Id);

        // Even when the customer tries to filter by someone else's id, they only see their own.
        var visible = await CreateService(customerUser)
            .ListAsync(customerId: null, Paging.Normalize(1, 50), CancellationToken.None);
        visible.Items.Should().OnlyContain(p => p.CustomerId == customerA.Id);
        visible.Items.Should().HaveCount(1);
        visible.TotalCount.Should().Be(1);

        var brokerView = await broker.ListAsync(customerId: null, Paging.Normalize(1, 50), CancellationToken.None);
        brokerView.Items.Should().HaveCount(2);
        brokerView.TotalCount.Should().Be(2);
    }

    [Fact]
    public async Task CancelAsync_WritesCancelledEventAndAudit()
    {
        var (_, quote) = await SeedIssuedQuoteAsync();
        var service = CreateService();
        var bound = await service.BindAsync(new BindPolicyRequest(quote.Id, "BRK-001"), CancellationToken.None);

        var cancelled = await service.CancelAsync(bound.Id, "customer request", CancellationToken.None);

        cancelled.Status.Should().Be("Cancelled");
        await using var db = _db.CreateDbContext();
        db.OutboxMessages.Count(m => m.EventType == "PolicyCancelledEvent").Should().Be(1);
        db.AuditEntries.Count(a => a.Action == "PolicyCancelled").Should().Be(1);
    }

    public void Dispose() => _db.Dispose();
}
