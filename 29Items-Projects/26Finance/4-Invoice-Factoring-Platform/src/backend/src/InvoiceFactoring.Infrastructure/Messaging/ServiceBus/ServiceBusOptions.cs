namespace InvoiceFactoring.Infrastructure.Messaging.ServiceBus;

/// <summary>Bound from the "ServiceBus" configuration section.</summary>
public sealed class ServiceBusOptions
{
    public const string SectionName = "ServiceBus";

    public string ConnectionString { get; set; } = string.Empty;
    public string UnderwritingQueue { get; set; } = "invoice-underwriting";
    public string PaymentsQueue { get; set; } = "payment-events";
}
