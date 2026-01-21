using Microsoft.Extensions.Options;
using MongoDB.Driver;
using TeamChat.Core.Entities;

namespace TeamChat.Infrastructure.MongoDB;

public class MongoDbContext
{
    private readonly IMongoDatabase _database;

    public MongoDbContext(IOptions<MongoDbSettings> settings)
    {
        var client = new MongoClient(settings.Value.ConnectionString);
        _database = client.GetDatabase(settings.Value.DatabaseName);

        // Create indexes on initialization
        CreateIndexesAsync().GetAwaiter().GetResult();
    }

    public IMongoCollection<User> Users => _database.GetCollection<User>("users");
    public IMongoCollection<Message> Messages => _database.GetCollection<Message>("messages");
    public IMongoCollection<Channel> Channels => _database.GetCollection<Channel>("channels");
    public IMongoCollection<MessageThread> Threads => _database.GetCollection<MessageThread>("threads");

    private async Task CreateIndexesAsync()
    {
        // Message indexes
        var messageIndexes = new[]
        {
            new CreateIndexModel<Message>(
                Builders<Message>.IndexKeys
                    .Ascending(m => m.ChannelId)
                    .Descending(m => m.CreatedAt)),
            new CreateIndexModel<Message>(
                Builders<Message>.IndexKeys.Ascending(m => m.ThreadId)),
            new CreateIndexModel<Message>(
                Builders<Message>.IndexKeys.Ascending(m => m.SenderId))
        };
        await Messages.Indexes.CreateManyAsync(messageIndexes);

        // Channel indexes
        var channelIndexes = new[]
        {
            new CreateIndexModel<Channel>(
                Builders<Channel>.IndexKeys.Ascending(c => c.Name)),
            new CreateIndexModel<Channel>(
                Builders<Channel>.IndexKeys.Descending(c => c.LastMessageAt))
        };
        await Channels.Indexes.CreateManyAsync(channelIndexes);

        // User indexes
        var userIndexes = new[]
        {
            new CreateIndexModel<User>(
                Builders<User>.IndexKeys.Ascending(u => u.Username),
                new CreateIndexOptions { Unique = true }),
            new CreateIndexModel<User>(
                Builders<User>.IndexKeys.Ascending(u => u.Email),
                new CreateIndexOptions { Unique = true })
        };
        await Users.Indexes.CreateManyAsync(userIndexes);

        // Thread indexes
        var threadIndexes = new[]
        {
            new CreateIndexModel<MessageThread>(
                Builders<MessageThread>.IndexKeys.Ascending(t => t.ParentMessageId)),
            new CreateIndexModel<MessageThread>(
                Builders<MessageThread>.IndexKeys.Ascending(t => t.ChannelId))
        };
        await Threads.Indexes.CreateManyAsync(threadIndexes);
    }
}
