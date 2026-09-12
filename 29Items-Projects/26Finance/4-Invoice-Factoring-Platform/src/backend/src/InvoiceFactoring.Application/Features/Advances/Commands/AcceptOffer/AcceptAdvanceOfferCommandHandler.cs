using InvoiceFactoring.Application.Abstractions.Payments;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Common.Exceptions;
using InvoiceFactoring.Application.Features.Advances.Dtos;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Exceptions;
using MediatR;
using Microsoft.Extensions.Logging;

namespace InvoiceFactoring.Application.Features.Advances.Commands.AcceptOffer;

/// <summary>
/// Disburses the advance: transition the invoice to Disbursing, create the Advance ledger
/// record, and initiate a Stripe payout to the borrower's connected account. All DB writes
/// commit in one unit of work; the payout call carries an idempotency key (TECH-NOTES §3.6).
/// Final "Outstanding" state is confirmed later by the Stripe <c>payout.paid</c> webhook.
/// </summary>
public sealed class AcceptAdvanceOfferCommandHandler
    : IRequestHandler<AcceptAdvanceOfferCommand, AdvanceDto>
{
    private readonly IInvoiceRepository _invoices;
    private readonly ICompanyRepository _companies;
    private readonly IAdvanceRepository _advances;
    private readonly IPaymentGateway _payments;
    private readonly IUnitOfWork _unitOfWork;
    private readonly ILogger<AcceptAdvanceOfferCommandHandler> _logger;

    public AcceptAdvanceOfferCommandHandler(
        IInvoiceRepository invoices,
        ICompanyRepository companies,
        IAdvanceRepository advances,
        IPaymentGateway payments,
        IUnitOfWork unitOfWork,
        ILogger<AcceptAdvanceOfferCommandHandler> logger)
    {
        _invoices = invoices;
        _companies = companies;
        _advances = advances;
        _payments = payments;
        _unitOfWork = unitOfWork;
        _logger = logger;
    }

    public async Task<AdvanceDto> Handle(
        AcceptAdvanceOfferCommand request,
        CancellationToken cancellationToken)
    {
        var invoice = await _invoices.GetWithAssessmentAsync(request.InvoiceId, cancellationToken)
            ?? throw new NotFoundException(nameof(Invoice), request.InvoiceId);

        var company = await _companies.GetByIdAsync(invoice.CompanyId, cancellationToken)
            ?? throw new NotFoundException(nameof(Company), invoice.CompanyId);

        if (company.StripeConnectedAccountId is null)
            throw new DomainException("Borrower has no connected payout account.");

        // Domain guards: only an Approved invoice can move to Disbursing.
        invoice.AcceptOffer();
        var advance = Advance.Create(invoice);

        // Initiate the payout. Idempotency key makes a retry safe.
        var payout = await _payments.CreatePayoutAsync(
            company.StripeConnectedAccountId,
            advance.NetDisbursed,
            request.IdempotencyKey,
            cancellationToken);

        advance.AttachPayout(payout.PayoutId);

        await _advances.AddAsync(advance, cancellationToken);
        _invoices.Update(invoice);
        await _unitOfWork.SaveChangesAsync(cancellationToken);

        _logger.LogInformation(
            "Advance {AdvanceId} created for invoice {InvoiceId}; payout {PayoutId} initiated.",
            advance.Id, invoice.Id, payout.PayoutId);

        return AdvanceDto.FromEntity(advance);
    }
}
