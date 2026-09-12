using Microsoft.ML.Data;

namespace InvoiceFactoring.ML.Models;

/// <summary>
/// Feature schema fed to the default-risk model. <c>LoadColumn</c> indices map to the
/// training CSV; the same fields are populated at inference time. Keep this in lockstep
/// with the feature computation in serving to avoid train/serve skew (TECH-NOTES §3.6).
/// </summary>
public sealed class CreditRiskInput
{
    [LoadColumn(0)] public float InvoiceAmount { get; set; }
    [LoadColumn(1)] public float PaymentTermDays { get; set; }
    [LoadColumn(2)] public float DaysUntilDue { get; set; }
    [LoadColumn(3)] public float DebtorPriorInvoicesPaid { get; set; }
    [LoadColumn(4)] public float DebtorPriorInvoicesDefaulted { get; set; }
    [LoadColumn(5)] public float BorrowerMonthlyInflow { get; set; }
    [LoadColumn(6)] public float BorrowerMonthlyOutflow { get; set; }
    [LoadColumn(7)] public float BorrowerAverageDailyBalance { get; set; }
    [LoadColumn(8)] public float BorrowerTenureMonths { get; set; }

    /// <summary>Categorical industry code (one-hot encoded in the pipeline).</summary>
    [LoadColumn(9)] public string IndustryCode { get; set; } = "UNKNOWN";

    /// <summary>Label: did the invoice ultimately default? Only present in training data.</summary>
    [LoadColumn(10), ColumnName("Label")] public bool Defaulted { get; set; }
}
