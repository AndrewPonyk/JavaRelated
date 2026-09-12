using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Common.Models;
using InvoiceFactoring.Application.Features.Invoices.Dtos;
using MediatR;

namespace InvoiceFactoring.Application.Features.Invoices.Queries.ListCompanyInvoices;

public sealed record ListCompanyInvoicesQuery(Guid CompanyId, int Page = 1, int PageSize = 20)
    : IRequest<PagedResult<InvoiceDto>>;

public sealed class ListCompanyInvoicesQueryHandler
    : IRequestHandler<ListCompanyInvoicesQuery, PagedResult<InvoiceDto>>
{
    private const int MaxPageSize = 100;

    private readonly IInvoiceRepository _invoices;

    public ListCompanyInvoicesQueryHandler(IInvoiceRepository invoices) => _invoices = invoices;

    public async Task<PagedResult<InvoiceDto>> Handle(
        ListCompanyInvoicesQuery request,
        CancellationToken cancellationToken)
    {
        // Clamp paging so a caller can't request page 0 or an unbounded page size.
        var page = Math.Max(1, request.Page);
        var pageSize = Math.Clamp(request.PageSize, 1, MaxPageSize);

        var (items, total) = await _invoices.ListByCompanyAsync(
            request.CompanyId, page, pageSize, cancellationToken);

        var dtos = items.Select(InvoiceDto.FromEntity).ToList();
        return new PagedResult<InvoiceDto>(dtos, page, pageSize, total);
    }
}
