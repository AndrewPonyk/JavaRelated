using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;

namespace InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;

/// <summary>
/// Orchestrates invoice submission: build the aggregate (which raises
/// <c>InvoiceSubmittedEvent</c>), persist it, and commit. The DbContext dispatches the
/// domain event after commit, which queues the invoice for asynchronous underwriting.
/// </summary>
public sealed class SubmitInvoiceCommandHandler : IRequestHandler<SubmitInvoiceCommand, Guid>
{
    private readonly IInvoiceRepository _invoices;
    private readonly IUnitOfWork _unitOfWork;
    private readonly ILogger<SubmitInvoiceCommandHandler> _logger;

    public SubmitInvoiceCommandHandler(
        IInvoiceRepository invoices,
        IUnitOfWork unitOfWork,
        ILogger<SubmitInvoiceCommandHandler> logger)
    {
        _invoices = invoices;
        _unitOfWork = unitOfWork;
        _logger = logger;
    }

    public async Task<Guid> Handle(SubmitInvoiceCommand request, CancellationToken cancellationToken)
    {
        // TODO: verify the CompanyId belongs to the authenticated caller (resource-based authz)
        // and that the company has completed KYB + bank verification before allowing submission.

        var invoice = Invoice.Submit(
            request.CompanyId,
            request.DebtorName,
            request.DebtorTaxId,
            new Money(request.Amount, request.Currency),
            request.IssueDate,
            request.DueDate,
            request.DocumentUri);

        await _invoices.AddAsync(invoice, cancellationToken);
        await _unitOfWork.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Invoice {InvoiceId} submitted for company {CompanyId}.",
            invoice.Id, request.CompanyId);

        return invoice.Id;
    }
}
