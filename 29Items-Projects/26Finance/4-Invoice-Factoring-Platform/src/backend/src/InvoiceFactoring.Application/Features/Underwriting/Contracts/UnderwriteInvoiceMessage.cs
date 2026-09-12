namespace InvoiceFactoring.Application.Features.Underwriting.Contracts;

/// <summary>
/// Integration event placed on the Service Bus underwriting queue. Consumed by the
/// underwriting worker, which loads features, scores risk, and produces an offer.
/// </summary>
public sealed record UnderwriteInvoiceMessage(Guid InvoiceId, Guid CompanyId);
