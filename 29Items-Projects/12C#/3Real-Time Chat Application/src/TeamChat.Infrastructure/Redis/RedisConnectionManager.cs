using Microsoft.Extensions.Options;
using StackExchange.Redis;

namespace TeamChat.Infrastructure.Redis;

public class RedisConnectionManager : IDisposable
{
    private readonly Lazy<ConnectionMultiplexer> _connection;
    private readonly RedisSettings _settings;

    public RedisConnectionManager(IOptions<RedisSettings> settings)
    {
        _settings = settings.Value;
        _connection = new Lazy<ConnectionMultiplexer>(() =>
            ConnectionMultiplexer.Connect(_settings.ConnectionString));
    }

    public ConnectionMultiplexer Connection => _connection.Value;
    public IDatabase Database => Connection.GetDatabase();
    public ISubscriber Subscriber => Connection.GetSubscriber();
    public string InstanceName => _settings.InstanceName;

    public string GetKey(string key) => $"{_settings.InstanceName}{key}";

    public void Dispose()
    {
        if (_connection.IsValueCreated)
        {
            _connection.Value.Dispose();
        }
    }
}
