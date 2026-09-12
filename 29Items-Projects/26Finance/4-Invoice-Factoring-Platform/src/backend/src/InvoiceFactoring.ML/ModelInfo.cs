namespace InvoiceFactoring.ML;

/// <summary>Identifies the model artifact so scored decisions can be traced to a version.</summary>
public static class ModelInfo
{
    public const string Name = "credit-risk";

    /// <summary>Bump on every retrain; stored on each CreditAssessment for auditability.</summary>
    public const string Version = "v1.0.0";
}
