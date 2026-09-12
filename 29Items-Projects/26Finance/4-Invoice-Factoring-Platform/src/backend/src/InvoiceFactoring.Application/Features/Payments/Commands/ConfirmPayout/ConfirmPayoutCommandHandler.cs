using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Domain.Enums;
using MediatR;
using Microsoft.Extensions.Logging;

namespace InvoiceFactoring.Application.Features.Payments.Commands.ConfirmPayout;

public sealed class ConfirmPayoutCommandHandler : IRequestHandler<ConfirmPayoutCommand>
{
    private readonly IAdvanceRepository _advances;
    private readonly IInvoiceRepository _invoices;
    private readonly IUnitOfWork _unitOfWork;
    private readonly ILogger<ConfirmPayoutCommandHandler> _logger;

    public ConfirmPayoutCommandHandler(
        IAdvanceRepository advances,
        IInvoiceRepository invoices,
        IUnitOfWork unitOfWork,
        ILogger<ConfirmPayoutCommandHandler> logger)
    {
        _advances = advances;
        _invoices = invoices;
        _unitOfWork = unitOfWork;
        _logger = logger;
    }

    public async Task Handle(ConfirmPayoutCommand request, CancellationToken cancellationToken)
    {
        var advance = await _advances.GetByStripePayoutIdAsync(request.StripePayoutId, cancellationToken);
        if (advance is null)
        {
            _logger.LogWarning("Payout {PayoutId} not recognized; ignoring webhook.", request.StripePayoutId);
            return; // unknown / already-handled → idempotent no-op
        }

        if (advance.Status != AdvanceStatus.Pending)
        {
            _logger.LogInformation("Payout {PayoutId} already reconciled ({Status}); skipping.",
                request.StripePayoutId, advance.Status);
            return;
        }

        if (!request.Succeeded)
        {
            // TODO: roll the invoice back to Approved and notify the borrower of payout failure.
            _logger.LogError("Payout {PayoutId} failed.", request.StripePayoutId);
            return;
        }

        advance.MarkDisbursed();
        _advances.Update(advance);

        var invoice = await _invoices.GetByIdAsync(advance.InvoiceId, cancellationToken);
        invoice?.MarkOutstanding();
        if (invoice is not null) _invoices.Update(invoice);

        await _unitOfWork.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Advance {AdvanceId} disbursed; invoice {InvoiceId} now outstanding.",
            advance.Id, advance.InvoiceId);
    }
}
