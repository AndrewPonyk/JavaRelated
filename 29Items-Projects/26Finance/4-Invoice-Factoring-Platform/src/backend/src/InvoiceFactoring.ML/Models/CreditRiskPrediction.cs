using Microsoft.ML.Data;

namespace InvoiceFactoring.ML.Models;

/// <summary>Model output for a single invoice.</summary>
public sealed class CreditRiskPrediction
{
    /// <summary>True if the model predicts the invoice will default at the 0.5 cutoff.</summary>
    [ColumnName("PredictedLabel")] public bool WillDefault { get; set; }

    /// <summary>Calibrated probability of default in [0,1] — this is what drives pricing.</summary>
    public float Probability { get; set; }

    /// <summary>Raw model score (pre-calibration); useful for monitoring/drift.</summary>
    public float Score { get; set; }
}
