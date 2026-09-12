using MediatR;

namespace InvoiceFactoring.Application.Features.Underwriting.Commands.AssessInvoice;

/// <summary>
/// Underwrite a submitted invoice: gather features, score default risk, and apply the
/// resulting offer (or decline). Invoked by the underwriting worker when it consumes an
/// <c>UnderwriteInvoiceMessage</c> from the Service Bus.
/// </summary>
public sealed record AssessInvoiceCommand(Guid InvoiceId) : IRequest;
