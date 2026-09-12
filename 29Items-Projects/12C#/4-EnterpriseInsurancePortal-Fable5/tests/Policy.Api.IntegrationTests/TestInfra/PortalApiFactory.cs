using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Hosting;
using Policy.Api.Rating;
using Policy.Infrastructure;
using Portal.Shared.Kafka;

namespace Policy.Api.IntegrationTests.TestInfra;

/// <summary>
/// Boots the real Policy.Api pipeline (controllers, validation, middleware, auth,
/// services, outbox) against an in-memory SQLite database, with the gRPC rating
/// dependency replaced by a deterministic fake and Kafka replaced by a recording
/// publisher. The outbox relay is disabled so tests drive it explicitly.
/// </summary>
public class PortalApiFactory : WebApplicationFactory<Program>
{
    private readonly SqliteConnection _connection = new("DataSource=:memory:");

    public RecordingEventPublisher PublishedEvents { get; } = new();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSetting("Database:MigrateOnStartup", "false");
        builder.UseSetting("Outbox:Enabled", "false");
        builder.UseSetting("Rating:GrpcAddress", "");
        builder.UseSetting("Kafka:BootstrapServers", "");
        builder.UseSetting("Identity:Authority", "");

        builder.ConfigureServices(services =>
        {
            _connection.Open();
            services.RemoveAll<DbContextOptions<PolicyDbContext>>();
            services.RemoveAll<IDbContextFactory<PolicyDbContext>>();
            services.AddDbContextFactory<PolicyDbContext>(o => o.UseSqlite(_connection));

            services.RemoveAll<IRatingClient>();
            services.AddSingleton<IRatingClient, FakeRatingClient>();

            services.RemoveAll<IEventPublisher>();
            services.AddSingleton<IEventPublisher>(PublishedEvents);
        });
    }

    protected override IHost CreateHost(IHostBuilder builder)
    {
        var host = base.CreateHost(builder);
        using var scope = host.Services.CreateScope();
        var dbFactory = scope.ServiceProvider.GetRequiredService<IDbContextFactory<PolicyDbContext>>();
        using var db = dbFactory.CreateDbContext();
        db.Database.EnsureCreated();
        return host;
    }

    /// <summary>HTTP client authenticated (via the Dev scheme) as a broker.</summary>
    public HttpClient CreateBrokerClient()
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Add("X-Dev-Role", "Broker");
        client.DefaultRequestHeaders.Add("X-Dev-User", "it-broker");
        return client;
    }

    /// <summary>HTTP client authenticated (via the Dev scheme) as a specific customer.</summary>
    public HttpClient CreateCustomerClient(Guid customerId)
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Add("X-Dev-Role", "Customer");
        client.DefaultRequestHeaders.Add("X-Dev-User", $"customer:{customerId}");
        client.DefaultRequestHeaders.Add("X-Dev-CustomerId", customerId.ToString());
        return client;
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
        if (disposing)
        {
            _connection.Dispose();
        }
    }
}
