using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using InvoiceFactoring.Application.Features.Underwriting.Commands.AssessInvoice;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Infrastructure.Persistence;
using MediatR;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace InvoiceFactoring.IntegrationTests;

/// <summary>
/// Exercises the main borrower flow end-to-end against the real API + SQL Server container:
/// submit an invoice, run underwriting (the use case the worker would invoke), then read
/// the invoice back and assert it carries an approved offer.
/// </summary>
public sealed class InvoiceFlowTests : IClassFixture<CustomWebApplicationFactory>
{
    private readonly CustomWebApplicationFactory _factory;

    public InvoiceFlowTests(CustomWebApplicationFactory factory) => _factory = factory;

    [Fact]
    public async Task Submit_Underwrite_Get_ReturnsApprovedOffer()
    {
        // Seed a company to own the invoice.
        Guid companyId;
        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            var company = Company.Register("Acme Corp", "12-3456789", "acme@example.com");
            db.Companies.Add(company);
            await db.SaveChangesAsync();
            companyId = company.Id;
        }

        var client = _factory.CreateClient();

        // 1. Submit.
        var submit = await client.PostAsJsonAsync("/api/invoices", new
        {
            companyId,
            debtorName = "Globex",
            debtorTaxId = "98-7654321",
            amount = 10_000m,
            currency = "USD",
            issueDate = "2026-01-01",
            dueDate = "2026-03-01",
            documentUri = (string?)null,
        });
        submit.StatusCode.Should().Be(HttpStatusCode.Accepted);
        var submitted = await submit.Content.ReadFromJsonAsync<SubmitResponse>();
        submitted.Should().NotBeNull();

        // 2. Underwrite (the worker isn't running in-process, so drive the use case directly).
        using (var scope = _factory.Services.CreateScope())
        {
            var mediator = scope.ServiceProvider.GetRequiredService<ISender>();
            await mediator.Send(new AssessInvoiceCommand(submitted!.InvoiceId));
        }

        // 3. Read back → approved with an offer.
        var get = await client.GetAsync($"/api/invoices/{submitted!.InvoiceId}");
        get.StatusCode.Should().Be(HttpStatusCode.OK);
        var invoice = await get.Content.ReadFromJsonAsync<InvoiceResponse>();

        invoice.Should().NotBeNull();
        invoice!.Status.Should().Be("Approved");
        invoice.Offer.Should().NotBeNull();
        invoice.Offer!.NetDisbursement.Should().BeGreaterThan(0);
    }

    [Fact]
    public async Task Get_UnknownInvoice_Returns404()
    {
        var client = _factory.CreateClient();

        var response = await client.GetAsync($"/api/invoices/{Guid.NewGuid()}");

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    private sealed record SubmitResponse(Guid InvoiceId, string Status);
    private sealed record InvoiceResponse(string Status, OfferResponse? Offer);
    private sealed record OfferResponse(string RiskGrade, decimal NetDisbursement);
}
