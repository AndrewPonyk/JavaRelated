using TeamChat.API.Hubs;

namespace TeamChat.API.Extensions;

public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Add SignalR with optional Redis backplane.
    /// </summary>
    public static IServiceCollection AddSignalRWithBackplane(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var signalRBuilder = services.AddSignalR(options =>
        {
            options.EnableDetailedErrors = true;
            options.MaximumReceiveMessageSize = 1024 * 1024; // 1 MB
        });

        // Add Redis backplane if configured
        var redisConnectionString = configuration["Redis:ConnectionString"];
        if (!string.IsNullOrEmpty(redisConnectionString))
        {
            signalRBuilder.AddStackExchangeRedis(redisConnectionString, options =>
            {
                options.Configuration.ChannelPrefix = "TeamChat";
            });
        }

        return services;
    }

    /// <summary>
    /// Add CORS policy for development.
    /// </summary>
    public static IServiceCollection AddCorsPolicy(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        services.AddCors(options =>
        {
            options.AddDefaultPolicy(builder =>
            {
                var allowedOrigins = configuration.GetSection("Cors:AllowedOrigins").Get<string[]>()
                    ?? new[] { "http://localhost:5000", "https://localhost:5001" };

                builder
                    .WithOrigins(allowedOrigins)
                    .AllowAnyMethod()
                    .AllowAnyHeader()
                    .AllowCredentials();
            });
        });

        return services;
    }
}
