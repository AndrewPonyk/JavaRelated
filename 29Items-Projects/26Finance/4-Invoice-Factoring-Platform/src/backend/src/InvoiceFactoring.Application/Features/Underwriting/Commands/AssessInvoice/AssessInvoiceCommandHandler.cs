using System.Text.Json;
using InvoiceFactoring.Application.Abstractions.Banking;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Abstractions.Scoring;
using InvoiceFactoring.Application.Common.Exceptions;
using InvoiceFactoring.Domain.Entities;
using MediatR;
using Microsoft.Extensions.Logging;

namespace InvoiceFactoring.Application.Features.Underwriting.Commands.AssessInvoice;

/// <summary>
/// The automated underwriting pipeline (ARCHITECTURE §2.3): enrich the invoice with the
/// borrower's bank cash-flow features, score the probability of default with the ML model,
/// then derive pricing and approve or decline. Idempotent: re-scoring an already-decided
/// invoice is a no-op.
/// </summary>
public sealed class AssessInvoiceCommandHandler : IRequestHandler<AssessInvoiceCommand>
{
    // TODO: source from configuration (ML:ScoringThreshold) via an injected options type.
    private const double DeclineThreshold = 0.30;

    private readonly IInvoiceRepository _invoices;
    private readonly ICompanyRepository _companies;
    private readonly IBankDataProvider _bankData;
    private readonly ICreditScoringService _scoring;
    private readonly IUnitOfWork _unitOfWork;
    private readonly ILogger<AssessInvoiceCommandHandler> _logger;

    public AssessInvoiceCommandHandler(
        IInvoiceRepository invoices,
        ICompanyRepository companies,
        IBankDataProvider bankData,
        ICreditScoringService scoring,
        IUnitOfWork unitOfWork,
        ILogger<AssessInvoiceCommandHandler> logger)
    {
        _invoices = invoices;
        _companies = companies;
        _bankData = bankData;
        _scoring = scoring;
        _unitOfWork = unitOfWork;
        _logger = logger;
    }

    public async Task Handle(AssessInvoiceCommand request, CancellationToken cancellationToken)
    {
        var invoice = await _invoices.GetWithAssessmentAsync(request.InvoiceId, cancellationToken)
            ?? throw new NotFoundException(nameof(Invoice), request.InvoiceId);

        if (invoice.CreditAssessmentId is not null)
        {
            _logger.LogInformation("Invoice {InvoiceId} already assessed; skipping (idempotent).",
                invoice.Id);
            return;
        }

        var company = await _companies.GetByIdAsync(invoice.CompanyId, cancellationToken)
            ?? throw new NotFoundException(nameof(Company), invoice.CompanyId);

        invoice.MarkUnderReview();

        // 1. Enrich with banking cash-flow features (Plaid).
        var cashFlow = company.PlaidItemId is { } itemId
            ? await _bankData.GetCashFlowAsync(itemId, cancellationToken)
            : new BankCashFlowSnapshot(0, 0, 0, 0); // TODO: require verified bank before submit

        // 2. Build the model request. NOTE: compute features identically in training (§3.6).
        var scoringRequest = new CreditScoringRequest(
            InvoiceAmount: invoice.FaceValue.Amount,
            PaymentTermDays: invoice.DueDate.DayNumber - invoice.IssueDate.DayNumber,
            DaysUntilDue: invoice.DueDate.DayNumber - DateOnly.FromDateTime(DateTime.UtcNow).DayNumber,
            DebtorPriorInvoicesPaid: 0,        // TODO: look up debtor payment history
            DebtorPriorInvoicesDefaulted: 0,   // TODO: look up debtor payment history
            BorrowerMonthlyInflow: cashFlow.MonthlyInflow,
            BorrowerMonthlyOutflow: cashFlow.MonthlyOutflow,
            BorrowerAverageDailyBalance: cashFlow.AverageDailyBalance,
            BorrowerTenureMonths: cashFlow.AccountTenureMonths,
            IndustryCode: "UNKNOWN");          // TODO: from company profile

        // 3. Score default risk.
        var result = await _scoring.ScoreAsync(scoringRequest, cancellationToken);

        // 4. Derive pricing + decision and apply to the aggregate.
        var assessment = CreditAssessment.FromScore(
            invoice.Id,
            result.ProbabilityOfDefault,
            result.ModelVersion,
            JsonSerializer.Serialize(result.ReasonCodes),
            DeclineThreshold);

        invoice.ApplyAssessment(assessment);

        // The invoice is already tracked, so change tracking persists the status change and
        // inserts the new CreditAssessment. Calling Update() here would wrongly mark the new
        // assessment as Modified and try to UPDATE a non-existent row.
        await _unitOfWork.SaveChangesAsync(cancellationToken);

        _logger.LogInformation(
            "Invoice {InvoiceId} assessed: PD={Pd:P1}, grade={Grade}, status={Status}.",
            invoice.Id, result.ProbabilityOfDefault, assessment.RiskGrade, invoice.Status);
    }
}
