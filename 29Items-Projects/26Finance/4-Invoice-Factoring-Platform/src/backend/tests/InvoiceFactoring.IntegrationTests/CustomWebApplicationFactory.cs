using InvoiceFactoring.Application.Abstractions.Messaging;
using InvoiceFactoring.Infrastructure.Persistence;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Testcontainers.MsSql;
using Xunit;

namespace InvoiceFactoring.IntegrationTests;

/// <summary>
/// Boots the real API against a throwaway SQL Server container (Testcontainers) so
/// integration tests exercise actual EF Core migrations and queries — not an in-memory fake.
/// </summary>
public sealed class CustomWebApplicationFactory : WebApplicationFactory<Program>, IAsyncLifetime
{
    private readonly MsSqlContainer _sqlContainer = new MsSqlBuilder()
        .WithImage("mcr.microsoft.com/mssql/server:2022-latest")
        .Build();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.ConfigureAppConfiguration((_, config) =>
        {
            config.AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["ConnectionStrings:SqlServer"] = _sqlContainer.GetConnectionString()
                // Stripe/Plaid/ServiceBus left empty → those paths are stubbed/guarded in tests.
            });
        });

        builder.ConfigureTestServices(services =>
        {
            // Authenticate as a Borrower without real Azure AD B2C.
            services.AddAuthentication(options =>
                {
                    options.DefaultAuthenticateScheme = TestAuthHandler.SchemeName;
                    options.DefaultChallengeScheme = TestAuthHandler.SchemeName;
                })
                .AddScheme<AuthenticationSchemeOptions, TestAuthHandler>(TestAuthHandler.SchemeName, _ => { });

            // No Service Bus in tests → swap the publisher for a no-op so domain-event
            // dispatch during submission doesn't try to reach a broker.
            services.RemoveAll<IIntegrationEventPublisher>();
            services.AddSingleton<IIntegrationEventPublisher, NoOpIntegrationEventPublisher>();
        });
    }

    public async Task InitializeAsync()
    {
        await _sqlContainer.StartAsync();

        using var scope = Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        await db.Database.MigrateAsync(); // apply real EF Core migrations against the container
    }

    public new async Task DisposeAsync() => await _sqlContainer.DisposeAsync();
}
