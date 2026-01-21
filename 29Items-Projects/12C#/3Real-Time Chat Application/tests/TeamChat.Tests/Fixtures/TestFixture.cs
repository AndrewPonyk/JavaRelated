using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using DotNet.Testcontainers.Builders;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.IdentityModel.Tokens;
using Testcontainers.MongoDb;
using Testcontainers.Redis;

namespace TeamChat.Tests.Fixtures;

/// <summary>
/// Base fixture for integration tests using Testcontainers.
/// Automatically starts MongoDB and Redis containers for testing.
/// </summary>
public class TestFixture : IAsyncLifetime
{
    private MongoDbContainer? _mongoContainer;
    private RedisContainer? _redisContainer;

    public WebApplicationFactory<Program> Factory { get; private set; } = null!;
    public HttpClient Client { get; private set; } = null!;

    public string MongoConnectionString => _mongoContainer?.GetConnectionString() ?? "mongodb://localhost:27017";
    public string RedisConnectionString => _redisContainer?.GetConnectionString() ?? "localhost:6379";

    // JWT settings for testing - must match the configuration in InitializeAsync
    private const string JwtSecret = "TestSecretKey_ThisMustBeAtLeast32CharactersLong!";
    private const string JwtIssuer = "TeamChatTest";
    private const string JwtAudience = "TeamChatTest";

    /// <summary>
    /// Generates a valid JWT token for testing authentication.
    /// </summary>
    public string GenerateTestToken(string userId, string userName, string? email = null)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(JwtSecret));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, userId),
            new(ClaimTypes.Name, userName),
            new(ClaimTypes.Email, email ?? $"{userId}@test.com"),
            new("display_name", userName)
        };

        var tokenDescriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(claims),
            Expires = DateTime.UtcNow.AddHours(1),
            Issuer = JwtIssuer,
            Audience = JwtAudience,
            SigningCredentials = credentials
        };

        var tokenHandler = new JwtSecurityTokenHandler();
        var token = tokenHandler.CreateToken(tokenDescriptor);
        return tokenHandler.WriteToken(token);
    }

    public async Task InitializeAsync()
    {
        // Start MongoDB container
        _mongoContainer = new MongoDbBuilder()
            .WithImage("mongo:7")
            .WithPortBinding(27017, true)
            .WithWaitStrategy(Wait.ForUnixContainer().UntilPortIsAvailable(27017))
            .Build();

        await _mongoContainer.StartAsync();

        // Start Redis container
        _redisContainer = new RedisBuilder()
            .WithImage("redis:7")
            .WithPortBinding(6379, true)
            .WithWaitStrategy(Wait.ForUnixContainer().UntilPortIsAvailable(6379))
            .Build();

        await _redisContainer.StartAsync();

        // Create WebApplicationFactory with container connection strings
        Factory = new WebApplicationFactory<Program>()
            .WithWebHostBuilder(builder =>
            {
                builder.ConfigureAppConfiguration((context, config) =>
                {
                    config.AddInMemoryCollection(new Dictionary<string, string?>
                    {
                        ["MongoDB:ConnectionString"] = MongoConnectionString,
                        ["MongoDB:DatabaseName"] = "TeamChat_Test",
                        ["Redis:ConnectionString"] = RedisConnectionString,
                        ["Redis:InstanceName"] = "TeamChatTest_",
                        ["Jwt:Secret"] = "TestSecretKey_ThisMustBeAtLeast32CharactersLong!",
                        ["Jwt:Issuer"] = "TeamChatTest",
                        ["Jwt:Audience"] = "TeamChatTest"
                    });
                });

                builder.ConfigureServices(services =>
                {
                    // Reconfigure JWT Bearer options to use test secret
                    // This is needed because Program.cs reads the JWT key before test config is applied
                    services.PostConfigure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme, options =>
                    {
                        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(JwtSecret));
                        options.TokenValidationParameters = new TokenValidationParameters
                        {
                            ValidateIssuerSigningKey = true,
                            IssuerSigningKey = key,
                            ValidateIssuer = true,
                            ValidIssuer = JwtIssuer,
                            ValidateAudience = true,
                            ValidAudience = JwtAudience,
                            ValidateLifetime = true,
                            ClockSkew = TimeSpan.Zero
                        };
                    });
                });
            });

        Client = Factory.CreateClient();
    }

    public async Task DisposeAsync()
    {
        Client?.Dispose();

        if (Factory != null)
        {
            await Factory.DisposeAsync();
        }

        if (_redisContainer != null)
        {
            await _redisContainer.StopAsync();
            await _redisContainer.DisposeAsync();
        }

        if (_mongoContainer != null)
        {
            await _mongoContainer.StopAsync();
            await _mongoContainer.DisposeAsync();
        }
    }
}

/// <summary>
/// Collection definition for sharing fixture across tests.
/// </summary>
[CollectionDefinition("Integration")]
public class IntegrationTestCollection : ICollectionFixture<TestFixture>
{
}
