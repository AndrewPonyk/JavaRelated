using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using TeamChat.Core.DTOs;
using TeamChat.Core.Enums;
using TeamChat.Tests.Fixtures;

namespace TeamChat.Tests.Integration;

[Collection("Integration")]
public class ApiEndpointTests
{
    private readonly TestFixture _fixture;
    private readonly HttpClient _client;

    public ApiEndpointTests(TestFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Client;
    }

    [Fact]
    public async Task HealthCheck_ShouldReturnHealthy()
    {
        // Act
        var response = await _client.GetAsync("/health");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetChannels_ShouldReturnOk()
    {
        // Act
        var response = await _client.GetAsync("/api/channels");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetChannels_ShouldReturnChannelList()
    {
        // Act
        var channels = await _client.GetFromJsonAsync<IEnumerable<ChannelDto>>("/api/channels");

        // Assert
        channels.Should().NotBeNull();
    }

    [Fact]
    public async Task CreateChannel_ShouldReturnCreated()
    {
        // Arrange
        var dto = new CreateChannelDto($"Test-{Guid.NewGuid():N}", "A test channel", ChannelType.Public);

        // Act
        var response = await _client.PostAsJsonAsync("/api/channels?userId=test-user", dto);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.Created);

        var channel = await response.Content.ReadFromJsonAsync<ChannelDto>();
        channel.Should().NotBeNull();
        channel!.Name.Should().StartWith("Test-");
    }

    [Fact]
    public async Task GetNonExistentChannel_ShouldReturnNotFound()
    {
        // Act
        var response = await _client.GetAsync("/api/channels/000000000000000000000000");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task RegisterUser_ShouldReturnToken()
    {
        // Arrange
        var uniqueId = Guid.NewGuid().ToString("N")[..8];
        var registerDto = new RegisterDto(
            Username: $"testuser_{uniqueId}",
            Email: $"test_{uniqueId}@example.com",
            Password: "password123",
            DisplayName: "Test User"
        );

        // Act
        var response = await _client.PostAsJsonAsync("/api/auth/register", registerDto);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.Created);

        var authResponse = await response.Content.ReadFromJsonAsync<AuthResponseDto>();
        authResponse.Should().NotBeNull();
        authResponse!.Token.Should().NotBeNullOrEmpty();
        authResponse.UserId.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task Login_WithValidCredentials_ShouldReturnToken()
    {
        // Arrange - First register a user
        var uniqueId = Guid.NewGuid().ToString("N")[..8];
        var username = $"logintest_{uniqueId}";
        var password = "password123";

        var registerDto = new RegisterDto(
            Username: username,
            Email: $"login_{uniqueId}@example.com",
            Password: password,
            DisplayName: "Login Test User"
        );

        await _client.PostAsJsonAsync("/api/auth/register", registerDto);

        // Act - Now login
        var loginDto = new LoginDto(username, password);
        var response = await _client.PostAsJsonAsync("/api/auth/login", loginDto);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var authResponse = await response.Content.ReadFromJsonAsync<AuthResponseDto>();
        authResponse.Should().NotBeNull();
        authResponse!.Token.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task Login_WithInvalidCredentials_ShouldReturnUnauthorized()
    {
        // Arrange
        var loginDto = new LoginDto("nonexistent_user", "wrongpassword");

        // Act
        var response = await _client.PostAsJsonAsync("/api/auth/login", loginDto);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task GetMessages_ForChannel_ShouldReturnList()
    {
        // Arrange - Create a channel first
        var dto = new CreateChannelDto($"MsgTest-{Guid.NewGuid():N}", "Message test channel", ChannelType.Public);
        var createResponse = await _client.PostAsJsonAsync("/api/channels?userId=test-user", dto);
        var channel = await createResponse.Content.ReadFromJsonAsync<ChannelDto>();

        // Act
        var response = await _client.GetAsync($"/api/messages/channel/{channel!.Id}");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var messages = await response.Content.ReadFromJsonAsync<IEnumerable<MessageDto>>();
        messages.Should().NotBeNull();
    }

    [Fact]
    public async Task CreateUser_ShouldReturnCreated()
    {
        // Arrange
        var uniqueId = Guid.NewGuid().ToString("N")[..8];
        var createUserDto = new CreateUserDto(
            Username: $"apiuser_{uniqueId}",
            Email: $"apiuser_{uniqueId}@example.com",
            Password: "password123",
            DisplayName: "API Test User"
        );

        // Act
        var response = await _client.PostAsJsonAsync("/api/users", createUserDto);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.Created);

        var user = await response.Content.ReadFromJsonAsync<UserDto>();
        user.Should().NotBeNull();
        user!.Username.Should().StartWith("apiuser_");
    }

    [Fact]
    public async Task GetUser_ByUsername_ShouldReturnUser()
    {
        // Arrange - Create user first
        var uniqueId = Guid.NewGuid().ToString("N")[..8];
        var username = $"getuser_{uniqueId}";
        var createUserDto = new CreateUserDto(
            Username: username,
            Email: $"getuser_{uniqueId}@example.com",
            Password: "password123",
            DisplayName: "Get User Test"
        );
        await _client.PostAsJsonAsync("/api/users", createUserDto);

        // Act
        var response = await _client.GetAsync($"/api/users/username/{username}");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var user = await response.Content.ReadFromJsonAsync<UserDto>();
        user.Should().NotBeNull();
        user!.Username.Should().Be(username);
    }

    [Fact]
    public async Task JoinChannel_ShouldSucceed()
    {
        // Arrange - Create channel and user
        var channelDto = new CreateChannelDto($"JoinTest-{Guid.NewGuid():N}", "Join test channel", ChannelType.Public);
        var channelResponse = await _client.PostAsJsonAsync("/api/channels?userId=creator", channelDto);
        var channel = await channelResponse.Content.ReadFromJsonAsync<ChannelDto>();

        var uniqueId = Guid.NewGuid().ToString("N")[..8];
        var createUserDto = new CreateUserDto(
            Username: $"joiner_{uniqueId}",
            Email: $"joiner_{uniqueId}@example.com",
            Password: "password123",
            DisplayName: "Joiner"
        );
        var userResponse = await _client.PostAsJsonAsync("/api/users", createUserDto);
        var user = await userResponse.Content.ReadFromJsonAsync<UserDto>();

        // Act
        var response = await _client.PostAsync($"/api/channels/{channel!.Id}/join?userId={user!.Id}", null);

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }
}
