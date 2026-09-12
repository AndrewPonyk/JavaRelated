using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Common.Exceptions;
using InvoiceFactoring.Application.Features.Invoices.Queries.GetInvoiceById;
using InvoiceFactoring.Application.Features.Invoices.Queries.ListCompanyInvoices;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.ValueObjects;
using NSubstitute;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class QueryHandlerTests
{
    private readonly IInvoiceRepository _invoices = Substitute.For<IInvoiceRepository>();

    private static Invoice NewInvoice(Guid? companyId = null) => Invoice.Submit(
        companyId ?? Guid.NewGuid(), "Acme", "12-3456789", new Money(10_000m),
        new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));

    [Fact]
    public async Task GetInvoiceById_WhenFound_ReturnsDto()
    {
        var invoice = NewInvoice();
        _invoices.GetWithAssessmentAsync(invoice.Id, Arg.Any<CancellationToken>()).Returns(invoice);

        var dto = await new GetInvoiceByIdQueryHandler(_invoices)
            .Handle(new GetInvoiceByIdQuery(invoice.Id), CancellationToken.None);

        dto.Id.Should().Be(invoice.Id);
        dto.Status.Should().Be("Submitted");
    }

    [Fact]
    public async Task GetInvoiceById_WhenMissing_ThrowsNotFound()
    {
        _invoices.GetWithAssessmentAsync(Arg.Any<Guid>(), Arg.Any<CancellationToken>())
            .Returns((Invoice?)null);

        var act = () => new GetInvoiceByIdQueryHandler(_invoices)
            .Handle(new GetInvoiceByIdQuery(Guid.NewGuid()), CancellationToken.None);

        await act.Should().ThrowAsync<NotFoundException>();
    }

    [Fact]
    public async Task ListCompanyInvoices_ClampsPagingAndMaps()
    {
        var companyId = Guid.NewGuid();
        _invoices.ListByCompanyAsync(companyId, Arg.Any<int>(), Arg.Any<int>(), Arg.Any<CancellationToken>())
            .Returns((new List<Invoice> { NewInvoice(companyId) }, 1));

        // Page 0 and an oversized page size should be clamped to 1 / 100.
        var result = await new ListCompanyInvoicesQueryHandler(_invoices)
            .Handle(new ListCompanyInvoicesQuery(companyId, Page: 0, PageSize: 500), CancellationToken.None);

        result.Page.Should().Be(1);
        result.PageSize.Should().Be(100);
        result.TotalCount.Should().Be(1);
        result.Items.Should().ContainSingle();
    }
}
