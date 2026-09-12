using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Policy.Infrastructure;

namespace Policy.Api.IntegrationTests.TestInfra;

/// <summary>In-memory SQLite database + IDbContextFactory for job-level tests.</summary>
public sealed class SqliteDb : IDbContextFactory<PolicyDbContext>, IDisposable
{
    private readonly SqliteConnection _connection;
    private readonly DbContextOptions<PolicyDbContext> _options;

    public SqliteDb()
    {
        _connection = new SqliteConnection("DataSource=:memory:");
        _connection.Open();
        _options = new DbContextOptionsBuilder<PolicyDbContext>()
            .UseSqlite(_connection)
            .Options;
        using var db = CreateDbContext();
        db.Database.EnsureCreated();
    }

    public PolicyDbContext CreateDbContext() => new(_options);

    public void Dispose() => _connection.Dispose();
}
