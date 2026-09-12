namespace InvoiceFactoring.Infrastructure.Banking.Plaid;

/// <summary>Bound from the "Plaid" configuration section.</summary>
public sealed class PlaidOptions
{
    public const string SectionName = "Plaid";

    public string ClientId { get; set; } = string.Empty;
    public string Secret { get; set; } = string.Empty;

    /// <summary>"sandbox" | "development" | "production".</summary>
    public string Environment { get; set; } = "sandbox";
}
