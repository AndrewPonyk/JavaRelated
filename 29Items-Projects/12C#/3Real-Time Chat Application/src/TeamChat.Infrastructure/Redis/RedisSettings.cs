namespace TeamChat.Infrastructure.Redis;

public class RedisSettings
{
    public const string SectionName = "Redis";

    public string ConnectionString { get; set; } = "localhost:6379";
    public string InstanceName { get; set; } = "TeamChat_";

    /// <summary>
    /// TTL for presence keys in seconds.
    /// </summary>
    public int PresenceTtlSeconds { get; set; } = 300;

    /// <summary>
    /// TTL for typing indicator keys in seconds.
    /// </summary>
    public int TypingTtlSeconds { get; set; } = 5;
}
