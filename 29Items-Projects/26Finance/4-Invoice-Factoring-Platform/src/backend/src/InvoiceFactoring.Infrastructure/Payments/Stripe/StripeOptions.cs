namespace InvoiceFactoring.Infrastructure.Payments.Stripe;

/// <summary>Bound from the "Stripe" configuration section (sourced from Key Vault in Azure).</summary>
public sealed class StripeOptions
{
    public const string SectionName = "Stripe";

    public string SecretKey { get; set; } = string.Empty;
    public string PublishableKey { get; set; } = string.Empty;
    public string WebhookSecret { get; set; } = string.Empty;
    public string ConnectClientId { get; set; } = string.Empty;
}
