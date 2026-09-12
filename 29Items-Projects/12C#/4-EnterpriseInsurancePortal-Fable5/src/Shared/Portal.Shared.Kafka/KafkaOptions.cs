namespace Portal.Shared.Kafka;

public class KafkaOptions
{
    public const string SectionName = "Kafka";

    /// <summary>Empty disables Kafka — events are then logged by <see cref="LoggingEventPublisher"/> instead.</summary>
    public string BootstrapServers { get; set; } = string.Empty;

    public string PolicyEventsTopic { get; set; } = "policy-events";

    public bool IsConfigured => !string.IsNullOrWhiteSpace(BootstrapServers);
}
