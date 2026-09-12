using MediatR;

namespace InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;

/// <summary>Submit a customer invoice for factoring. Returns the new invoice id.</summary>
public sealed record SubmitInvoiceCommand(
    Guid CompanyId,
    string DebtorName,
    string DebtorTaxId,
    decimal Amount,
    string Currency,
    DateOnly IssueDate,
    DateOnly DueDate,
    string? DocumentUri) : IRequest<Guid>;
