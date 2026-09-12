namespace InvoiceFactoring.Application.Abstractions.Scoring;

/// <summary>
/// Abstraction over the ML default-risk model. The Infrastructure layer adapts this to
/// the ML.NET predictor in InvoiceFactoring.ML, mapping these neutral contracts to the
/// model's input/output schema. Keeping it here means Application/Domain never depend on
/// ML.NET — and the model can be swapped (ONNX, remote endpoint) without touching use cases.
/// </summary>
public interface ICreditScoringService
{
    Task<CreditScoringResult> ScoreAsync(CreditScoringRequest request, CancellationToken ct = default);
}

/// <summary>Features fed to the model. Compute these the SAME way in training and serving
/// to avoid train/serve skew (TECH-NOTES §3.6).</summary>
public sealed record CreditScoringRequest(
    decimal InvoiceAmount,
    int PaymentTermDays,
    int DaysUntilDue,
    int DebtorPriorInvoicesPaid,
    int DebtorPriorInvoicesDefaulted,
    double BorrowerMonthlyInflow,
    double BorrowerMonthlyOutflow,
    double BorrowerAverageDailyBalance,
    int BorrowerTenureMonths,
    string IndustryCode);

public sealed record CreditScoringResult(
    double ProbabilityOfDefault,
    string ModelVersion,
    IReadOnlyList<FeatureContribution> ReasonCodes);

/// <summary>A single feature's contribution to the score — drives adverse-action reasons.</summary>
public sealed record FeatureContribution(string Feature, double Contribution);
