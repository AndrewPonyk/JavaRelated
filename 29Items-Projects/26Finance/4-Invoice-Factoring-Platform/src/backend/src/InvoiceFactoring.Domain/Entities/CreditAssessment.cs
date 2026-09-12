using InvoiceFactoring.Domain.Common;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.ValueObjects;

namespace InvoiceFactoring.Domain.Entities;

/// <summary>
/// The output of underwriting a single invoice: the model's probability of default,
/// the derived risk grade, and the resulting pricing (advance rate + discount fee).
/// Persisted alongside the reason codes that justify the decision (for adverse-action
/// notices / fair-lending review — see ARCHITECTURE §2.5).
/// </summary>
public class CreditAssessment : Entity
{
    public Guid InvoiceId { get; private set; }

    /// <summary>Model output in [0,1]; higher = more likely the debtor fails to pay.</summary>
    public double ProbabilityOfDefault { get; private set; }

    public RiskGrade RiskGrade { get; private set; }

    /// <summary>Fraction of face value advanced upfront (e.g. 0.85 = 85%).</summary>
    public decimal AdvanceRate { get; private set; }

    /// <summary>Platform discount fee as a fraction of face value (revenue).</summary>
    public decimal DiscountFeeRate { get; private set; }

    /// <summary>Top model features that drove the score — JSON, for explainability.</summary>
    public string ReasonCodesJson { get; private set; } = "[]";

    public string ModelVersion { get; private set; } = "unknown";

    private CreditAssessment() { } // EF Core

    /// <summary>
    /// Builds an assessment from a model score and applies the pricing rule table.
    /// <paramref name="declineThreshold"/> comes from config (ML:ScoringThreshold).
    /// </summary>
    public static CreditAssessment FromScore(
        Guid invoiceId,
        double probabilityOfDefault,
        string modelVersion,
        string reasonCodesJson,
        double declineThreshold = 0.30)
    {
        var (grade, advanceRate, feeRate) = PriceFromPd(probabilityOfDefault, declineThreshold);

        return new CreditAssessment
        {
            InvoiceId = invoiceId,
            ProbabilityOfDefault = probabilityOfDefault,
            RiskGrade = grade,
            AdvanceRate = advanceRate,
            DiscountFeeRate = feeRate,
            ModelVersion = modelVersion,
            ReasonCodesJson = reasonCodesJson
        };
    }

    public bool IsApproved => RiskGrade != RiskGrade.F;

    /// <summary>Amount advanced to the borrower upfront, before the fee is netted out.</summary>
    public Money GrossAdvance(Money faceValue) => faceValue.Multiply(AdvanceRate);

    /// <summary>The platform's discount fee for factoring this invoice.</summary>
    public Money Fee(Money faceValue) => faceValue.Multiply(DiscountFeeRate);

    /// <summary>What actually lands in the borrower's bank today: gross advance − fee.</summary>
    public Money NetDisbursement(Money faceValue) => GrossAdvance(faceValue).Subtract(Fee(faceValue));

    // ── Pricing rule table ────────────────────────────────────────────────────────
    // TODO: externalize to a versioned, A/B-testable pricing strategy (see Phase 3).
    private static (RiskGrade Grade, decimal AdvanceRate, decimal FeeRate) PriceFromPd(
        double pd, double declineThreshold)
    {
        if (pd > declineThreshold) return (RiskGrade.F, 0m, 0m);
        return pd switch
        {
            <= 0.03 => (RiskGrade.A, 0.90m, 0.015m),
            <= 0.08 => (RiskGrade.B, 0.85m, 0.025m),
            <= 0.15 => (RiskGrade.C, 0.80m, 0.040m),
            _ => (RiskGrade.D, 0.70m, 0.060m)
        };
    }
}
