namespace InvoiceFactoring.Infrastructure.Scoring;

/// <summary>Bound from the "ML" configuration section.</summary>
public sealed class MlScoringOptions
{
    public const string SectionName = "ML";

    /// <summary>Path/URI of the serialized model artifact (local path or Blob reference).</summary>
    public string ModelPath { get; set; } = "./models/credit-risk-v1.zip";

    /// <summary>PD above which an invoice is auto-declined.</summary>
    public double ScoringThreshold { get; set; } = 0.30;
}
