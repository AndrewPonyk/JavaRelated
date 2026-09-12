using InvoiceFactoring.Application.Abstractions.Scoring;

namespace InvoiceFactoring.Infrastructure.Scoring;

/// <summary>
/// Transparent, deterministic default-risk scorer used until a trained ML.NET model is
/// published. It produces a calibrated probability of default from a logistic combination
/// of the same features the ML model consumes, plus signed per-feature contributions that
/// serve as adverse-action reason codes (ARCHITECTURE §2.5).
///
/// <para>This keeps the underwriting flow fully functional with no binary artifact. When a
/// model file exists, DI swaps in <see cref="MlNetCreditScoringService"/> instead.</para>
/// </summary>
public sealed class HeuristicCreditScoringService : ICreditScoringService
{
    public const string ModelVersion = "heuristic-v1";

    // Logistic weights. Intercept targets a ~7% baseline PD; positive weights raise risk.
    private const double Intercept = -2.5;
    private const double WeightDebtorDefaultHistory = 3.5;
    private const double WeightPaymentTerm = 0.7;
    private const double WeightInvoiceSize = 0.5;
    private const double WeightCashCoverage = 0.9;
    private const double WeightTenureProtection = -0.9; // negative: tenure reduces risk

    public Task<CreditScoringResult> ScoreAsync(CreditScoringRequest request, CancellationToken ct = default)
    {
        // ── Feature engineering: normalize each signal to ~[0,1] (or [-1,0] for protective).
        // Keep this identical to the ML training feature computation to avoid train/serve skew.
        var totalDebtorInvoices = request.DebtorPriorInvoicesPaid + request.DebtorPriorInvoicesDefaulted;
        var defaultHistory = totalDebtorInvoices > 0
            ? (double)request.DebtorPriorInvoicesDefaulted / totalDebtorInvoices
            : 0.0;

        var termRisk = Clamp01(request.PaymentTermDays / 120.0);
        var sizeRisk = Clamp01(Math.Log10(Math.Max(1d, (double)request.InvoiceAmount)) / 6.0); // ~1.0 at $1M

        // Cash coverage only contributes when we actually have bank data (Plaid).
        double coverageRisk = 0.0;
        var haveBankData = request.BorrowerMonthlyInflow > 0;
        if (haveBankData)
        {
            var coverage = request.BorrowerMonthlyInflow / Math.Max(1d, (double)request.InvoiceAmount);
            coverageRisk = Clamp01(1.0 / (1.0 + coverage)); // low inflow vs invoice → higher risk
        }

        var tenureProtection = Clamp01(request.BorrowerTenureMonths / 60.0); // up to 5 years

        // ── Weighted contributions (the basis for reason codes).
        var contributions = new List<FeatureContribution>
        {
            new("DebtorDefaultHistory", WeightDebtorDefaultHistory * defaultHistory),
            new("PaymentTerm", WeightPaymentTerm * termRisk),
            new("InvoiceSize", WeightInvoiceSize * sizeRisk),
            new("CashFlowCoverage", WeightCashCoverage * coverageRisk),
            new("AccountTenure", WeightTenureProtection * tenureProtection),
        };

        var z = Intercept + contributions.Sum(c => c.Contribution);
        var probabilityOfDefault = Sigmoid(z);

        // Reason codes: the strongest drivers, largest absolute contribution first.
        var reasonCodes = contributions
            .Where(c => Math.Abs(c.Contribution) > 0.0001)
            .OrderByDescending(c => Math.Abs(c.Contribution))
            .ToList();

        var result = new CreditScoringResult(
            ProbabilityOfDefault: Math.Round(probabilityOfDefault, 4),
            ModelVersion: ModelVersion,
            ReasonCodes: reasonCodes);

        return Task.FromResult(result);
    }

    private static double Clamp01(double value) => Math.Clamp(value, 0.0, 1.0);

    private static double Sigmoid(double z) => 1.0 / (1.0 + Math.Exp(-z));
}
