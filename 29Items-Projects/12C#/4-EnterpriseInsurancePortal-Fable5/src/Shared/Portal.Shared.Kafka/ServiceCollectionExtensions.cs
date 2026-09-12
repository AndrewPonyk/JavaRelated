using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace Portal.Shared.Kafka;

public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Registers <see cref="KafkaOptions"/> and an <see cref="IEventPublisher"/> —
    /// the real Kafka producer when BootstrapServers is configured, a logging
    /// null-object otherwise.
    /// </summary>
    public static IServiceCollection AddKafkaEventPublishing(this IServiceCollection services, IConfiguration configuration)
    {
        var options = configuration.GetSection(KafkaOptions.SectionName).Get<KafkaOptions>() ?? new KafkaOptions();
        services.AddSingleton(options);

        if (options.IsConfigured)
        {
            services.AddSingleton<IEventPublisher>(_ => new KafkaEventPublisher(options));
        }
        else
        {
            services.AddSingleton<IEventPublisher>(sp =>
                new LoggingEventPublisher(sp.GetRequiredService<ILogger<LoggingEventPublisher>>()));
        }

        return services;
    }
}
