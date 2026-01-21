using TeamChat.Core.Entities;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Services;

/// <summary>
/// Seeds the database with example users and channels for development.
/// </summary>
public class DataSeeder
{
    private readonly IUserRepository _userRepository;
    private readonly IChannelRepository _channelRepository;
    private readonly ILogger<DataSeeder> _logger;

    public DataSeeder(
        IUserRepository userRepository,
        IChannelRepository channelRepository,
        ILogger<DataSeeder> logger)
    {
        _userRepository = userRepository;
        _channelRepository = channelRepository;
        _logger = logger;
    }

    public async Task SeedAsync()
    {
        await SeedUsersAsync();
        await SeedChannelsAsync();
    }

    private async Task SeedUsersAsync()
    {
        // Check if seed user already exists
        var existingUser = await _userRepository.GetByUsernameAsync("alice");
        if (existingUser != null)
        {
            _logger.LogInformation("Seed users already exist, skipping");
            return;
        }

        var users = new List<User>
        {
            new()
            {
                Username = "alice",
                Email = "alice@example.com",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword("password123"),
                DisplayName = "Alice Johnson",
                Status = UserStatus.Offline,
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                Username = "bob",
                Email = "bob@example.com",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword("password123"),
                DisplayName = "Bob Smith",
                Status = UserStatus.Offline,
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                Username = "charlie",
                Email = "charlie@example.com",
                PasswordHash = BCrypt.Net.BCrypt.HashPassword("password123"),
                DisplayName = "Charlie Brown",
                Status = UserStatus.Offline,
                CreatedAt = DateTime.UtcNow
            }
        };

        foreach (var user in users)
        {
            await _userRepository.CreateAsync(user);
            _logger.LogInformation("Created seed user: {Username}", user.Username);
        }

        _logger.LogInformation("Seeded {Count} users", users.Count);
    }

    private async Task SeedChannelsAsync()
    {
        var existingChannels = await _channelRepository.GetPublicChannelsAsync();
        if (existingChannels.Any())
        {
            _logger.LogInformation("Channels already exist, skipping seed");
            return;
        }

        var channels = new List<Channel>
        {
            new()
            {
                Name = "general",
                Description = "General discussion for everyone",
                Type = ChannelType.Public,
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                Name = "random",
                Description = "Random topics and fun stuff",
                Type = ChannelType.Public,
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                Name = "help",
                Description = "Get help and support",
                Type = ChannelType.Public,
                CreatedAt = DateTime.UtcNow
            }
        };

        foreach (var channel in channels)
        {
            await _channelRepository.CreateAsync(channel);
            _logger.LogInformation("Created seed channel: {Name}", channel.Name);
        }

        _logger.LogInformation("Seeded {Count} channels", channels.Count);
    }
}
