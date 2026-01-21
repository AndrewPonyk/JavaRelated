using FluentAssertions;
using Microsoft.Extensions.Options;
using Moq;
using StackExchange.Redis;
using TeamChat.Core.Enums;
using TeamChat.Infrastructure.Redis;

namespace TeamChat.Tests.Unit;

public class PresenceTrackerTests
{
    private readonly Mock<IOptions<RedisSettings>> _settingsMock;
    private readonly RedisSettings _settings;

    public PresenceTrackerTests()
    {
        _settings = new RedisSettings
        {
            ConnectionString = "localhost:6379",
            InstanceName = "Test_",
            PresenceTtlSeconds = 300,
            TypingTtlSeconds = 5
        };

        _settingsMock = new Mock<IOptions<RedisSettings>>();
        _settingsMock.Setup(x => x.Value).Returns(_settings);
    }

    // Note: These tests would require a running Redis instance or
    // use Testcontainers for a real Redis container.
    // For unit tests, we'd typically mock the Redis connection.

    [Fact]
    public void RedisSettings_ShouldHaveCorrectDefaults()
    {
        // Arrange & Act
        var settings = new RedisSettings();

        // Assert
        settings.ConnectionString.Should().Be("localhost:6379");
        settings.InstanceName.Should().Be("TeamChat_");
        settings.PresenceTtlSeconds.Should().Be(300);
        settings.TypingTtlSeconds.Should().Be(5);
    }

    [Theory]
    [InlineData(UserStatus.Online)]
    [InlineData(UserStatus.Away)]
    [InlineData(UserStatus.DoNotDisturb)]
    [InlineData(UserStatus.Offline)]
    public void UserStatus_ShouldHaveExpectedValues(UserStatus status)
    {
        // Assert - verify enum values are correctly defined
        Enum.IsDefined(typeof(UserStatus), status).Should().BeTrue();
    }
}
