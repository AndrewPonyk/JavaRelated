using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Common.Exceptions;
using InvoiceFactoring.Application.Features.Invoices.Dtos;
using MediatR;

namespace InvoiceFactoring.Application.Features.Invoices.Queries.GetInvoiceById;

public sealed record GetInvoiceByIdQuery(Guid InvoiceId) : IRequest<InvoiceDto>;

public sealed class GetInvoiceByIdQueryHandler : IRequestHandler<GetInvoiceByIdQuery, InvoiceDto>
{
    private readonly IInvoiceRepository _invoices;

    public GetInvoiceByIdQueryHandler(IInvoiceRepository invoices) => _invoices = invoices;

    public async Task<InvoiceDto> Handle(GetInvoiceByIdQuery request, CancellationToken cancellationToken)
    {
        var invoice = await _invoices.GetWithAssessmentAsync(request.InvoiceId, cancellationToken)
            ?? throw new NotFoundException(nameof(Domain.Entities.Invoice), request.InvoiceId);

        // TODO: resource-based authorization — caller may only read their own company's invoices.
        return InvoiceDto.FromEntity(invoice);
    }
}
