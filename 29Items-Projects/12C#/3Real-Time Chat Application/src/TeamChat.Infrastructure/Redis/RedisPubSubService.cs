using StackExchange.Redis;

namespace TeamChat.Infrastructure.Redis;

/// <summary>
/// Service for Redis pub/sub operations.
/// </summary>
public class RedisPubSubService
{
    private readonly RedisConnectionManager _redis;

    public RedisPubSubService(RedisConnectionManager redis)
    {
        _redis = redis;
    }

    /// <summary>
    /// Publishes a message to a channel.
    /// </summary>
    public async Task PublishAsync(string channel, string message)
    {
        await _redis.Subscriber.PublishAsync(
            RedisChannel.Literal(_redis.GetKey(channel)),
            message);
    }

    /// <summary>
    /// Subscribes to a channel and executes the handler when a message is received.
    /// </summary>
    public async Task SubscribeAsync(string channel, Action<string> handler)
    {
        await _redis.Subscriber.SubscribeAsync(
            RedisChannel.Literal(_redis.GetKey(channel)),
            (_, message) => handler(message.ToString()));
    }

    /// <summary>
    /// Unsubscribes from a channel.
    /// </summary>
    public async Task UnsubscribeAsync(string channel)
    {
        await _redis.Subscriber.UnsubscribeAsync(
            RedisChannel.Literal(_redis.GetKey(channel)));
    }

    /// <summary>
    /// Subscribes to presence updates.
    /// </summary>
    public async Task SubscribeToPresenceUpdatesAsync(Action<string, string> handler)
    {
        await _redis.Subscriber.SubscribeAsync(
            RedisChannel.Literal("presence:updates"),
            (_, message) =>
            {
                var parts = message.ToString().Split(':');
                if (parts.Length == 2)
                {
                    handler(parts[0], parts[1]);
                }
            });
    }
}
