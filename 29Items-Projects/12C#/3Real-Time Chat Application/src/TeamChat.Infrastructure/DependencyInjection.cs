using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using TeamChat.Core.Interfaces;
using TeamChat.Infrastructure.ML;
using TeamChat.Infrastructure.MongoDB;
using TeamChat.Infrastructure.MongoDB.Repositories;
using TeamChat.Infrastructure.Redis;
using TeamChat.Infrastructure.Storage;

namespace TeamChat.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        // MongoDB
        services.Configure<MongoDbSettings>(configuration.GetSection(MongoDbSettings.SectionName));
        services.AddSingleton<MongoDbContext>();

        // Repositories
        services.AddScoped<IUserRepository, UserRepository>();
        services.AddScoped<IMessageRepository, MessageRepository>();
        services.AddScoped<IChannelRepository, ChannelRepository>();
        services.AddScoped<IThreadRepository, ThreadRepository>();

        // Redis
        services.Configure<RedisSettings>(configuration.GetSection(RedisSettings.SectionName));
        services.AddSingleton<RedisConnectionManager>();
        services.AddSingleton<RedisPubSubService>();
        services.AddSingleton<IPresenceService, PresenceTracker>();

        // File Storage
        services.Configure<FileStorageSettings>(configuration.GetSection(FileStorageSettings.SectionName));
        services.AddScoped<IFileStorageService, LocalFileStorageService>();

        // ML.NET Sentiment Analysis
        services.AddSingleton<ISentimentAnalyzer, SentimentAnalysisService>();

        return services;
    }
}
