using Microsoft.Extensions.Options;
using StackExchange.Redis;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.Redis;

public class PresenceTracker : IPresenceService
{
    private readonly RedisConnectionManager _redis;
    private readonly RedisSettings _settings;

    private const string UserStatusKey = "user:status:";
    private const string UserConnectionsKey = "user:connections:";
    private const string TypingKey = "channel:typing:";
    private const string PresenceChannel = "presence:updates";

    public PresenceTracker(RedisConnectionManager redis, IOptions<RedisSettings> settings)
    {
        _redis = redis;
        _settings = settings.Value;
    }

    public async Task SetOnlineAsync(string userId, string connectionId, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var batch = db.CreateBatch();

        // Add connection to user's connection set
        var connectionsKey = _redis.GetKey($"{UserConnectionsKey}{userId}");
        batch.SetAddAsync(connectionsKey, connectionId);
        batch.KeyExpireAsync(connectionsKey, TimeSpan.FromSeconds(_settings.PresenceTtlSeconds));

        // Set user status to online
        var statusKey = _redis.GetKey($"{UserStatusKey}{userId}");
        batch.StringSetAsync(statusKey, UserStatus.Online.ToString(), TimeSpan.FromSeconds(_settings.PresenceTtlSeconds));

        batch.Execute();

        // Publish presence update
        await _redis.Subscriber.PublishAsync(
            RedisChannel.Literal(PresenceChannel),
            $"{userId}:{UserStatus.Online}");
    }

    public async Task SetOfflineAsync(string userId, string connectionId, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var connectionsKey = _redis.GetKey($"{UserConnectionsKey}{userId}");

        // Remove this connection
        await db.SetRemoveAsync(connectionsKey, connectionId);

        // Check if user has other connections
        var connectionCount = await db.SetLengthAsync(connectionsKey);
        if (connectionCount == 0)
        {
            // No more connections, set offline
            var statusKey = _redis.GetKey($"{UserStatusKey}{userId}");
            await db.StringSetAsync(statusKey, UserStatus.Offline.ToString());

            // Publish presence update
            await _redis.Subscriber.PublishAsync(
                RedisChannel.Literal(PresenceChannel),
                $"{userId}:{UserStatus.Offline}");
        }
    }

    public async Task SetStatusAsync(string userId, UserStatus status, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var statusKey = _redis.GetKey($"{UserStatusKey}{userId}");
        await db.StringSetAsync(statusKey, status.ToString(), TimeSpan.FromSeconds(_settings.PresenceTtlSeconds));

        // Publish presence update
        await _redis.Subscriber.PublishAsync(
            RedisChannel.Literal(PresenceChannel),
            $"{userId}:{status}");
    }

    public async Task<UserStatus> GetStatusAsync(string userId, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var statusKey = _redis.GetKey($"{UserStatusKey}{userId}");
        var value = await db.StringGetAsync(statusKey);

        if (value.IsNullOrEmpty)
            return UserStatus.Offline;

        return Enum.TryParse<UserStatus>(value.ToString(), out var status)
            ? status
            : UserStatus.Offline;
    }

    public async Task<IEnumerable<string>> GetOnlineUsersAsync(IEnumerable<string> userIds, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var onlineUsers = new List<string>();

        foreach (var userId in userIds)
        {
            var status = await GetStatusAsync(userId, cancellationToken);
            if (status != UserStatus.Offline)
            {
                onlineUsers.Add(userId);
            }
        }

        return onlineUsers;
    }

    public async Task<IEnumerable<string>> GetConnectionsAsync(string userId, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var connectionsKey = _redis.GetKey($"{UserConnectionsKey}{userId}");
        var connections = await db.SetMembersAsync(connectionsKey);
        return connections.Select(c => c.ToString());
    }

    public async Task SetTypingAsync(string userId, string channelId, bool isTyping, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var typingKey = _redis.GetKey($"{TypingKey}{channelId}");

        if (isTyping)
        {
            await db.SetAddAsync(typingKey, userId);
            await db.KeyExpireAsync(typingKey, TimeSpan.FromSeconds(_settings.TypingTtlSeconds));
        }
        else
        {
            await db.SetRemoveAsync(typingKey, userId);
        }
    }

    public async Task<IEnumerable<string>> GetTypingUsersAsync(string channelId, CancellationToken cancellationToken = default)
    {
        var db = _redis.Database;
        var typingKey = _redis.GetKey($"{TypingKey}{channelId}");
        var users = await db.SetMembersAsync(typingKey);
        return users.Select(u => u.ToString());
    }
}
