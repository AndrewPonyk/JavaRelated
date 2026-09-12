using InvoiceFactoring.Application.Abstractions.Scoring;
using InvoiceFactoring.ML;
using InvoiceFactoring.ML.Models;
using Microsoft.Extensions.ML;

namespace InvoiceFactoring.Infrastructure.Scoring;

/// <summary>
/// Adapts the ML.NET model (InvoiceFactoring.ML) to the <see cref="ICreditScoringService"/>
/// port. Uses a thread-safe, pooled <see cref="PredictionEnginePool{TSrc,TDst}"/> so the
/// model is loaded once and reused across concurrent requests (ARCHITECTURE §2.4).
/// </summary>
public sealed class MlNetCreditScoringService : ICreditScoringService
{
    private readonly PredictionEnginePool<CreditRiskInput, CreditRiskPrediction> _pool;

    public MlNetCreditScoringService(PredictionEnginePool<CreditRiskInput, CreditRiskPrediction> pool)
        => _pool = pool;

    public Task<CreditScoringResult> ScoreAsync(CreditScoringRequest request, CancellationToken ct = default)
    {
        var input = new CreditRiskInput
        {
            InvoiceAmount = (float)request.InvoiceAmount,
            PaymentTermDays = request.PaymentTermDays,
            DaysUntilDue = request.DaysUntilDue,
            DebtorPriorInvoicesPaid = request.DebtorPriorInvoicesPaid,
            DebtorPriorInvoicesDefaulted = request.DebtorPriorInvoicesDefaulted,
            BorrowerMonthlyInflow = (float)request.BorrowerMonthlyInflow,
            BorrowerMonthlyOutflow = (float)request.BorrowerMonthlyOutflow,
            BorrowerAverageDailyBalance = (float)request.BorrowerAverageDailyBalance,
            BorrowerTenureMonths = request.BorrowerTenureMonths,
            IndustryCode = request.IndustryCode
        };

        var prediction = _pool.Predict(ModelInfo.Name, input);

        // TODO: derive real per-feature reason codes (ML.NET CalculateFeatureContribution)
        // and surface them for adverse-action notices (ARCHITECTURE §2.5).
        var reasonCodes = new List<FeatureContribution>();

        var result = new CreditScoringResult(
            ProbabilityOfDefault: prediction.Probability,
            ModelVersion: ModelInfo.Version,
            ReasonCodes: reasonCodes);

        return Task.FromResult(result);
    }
}
