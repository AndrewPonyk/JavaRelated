using Hangfire;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using Policy.Infrastructure;
using Portal.Jobs.Jobs;
using Portal.Jobs.Rating;
using Rating.Grpc;

var builder = WebApplication.CreateBuilder(args);

var connectionString = builder.Configuration.GetConnectionString("PolicyDb")
    ?? throw new InvalidOperationException("ConnectionStrings:PolicyDb is required.");

builder.Services.AddDbContextFactory<PolicyDbContext>(o => o.UseSqlServer(connectionString));

builder.Services
    .AddGrpcClient<RatingService.RatingServiceClient>(o =>
        o.Address = new Uri(builder.Configuration["Rating:GrpcAddress"]
            ?? throw new InvalidOperationException("Rating:GrpcAddress is required.")));
builder.Services.AddScoped<IRatingClient, GrpcRatingClient>();

builder.Services.AddHangfire(config => config
    .SetDataCompatibilityLevel(CompatibilityLevel.Version_180)
    .UseSimpleAssemblyNameTypeSerializer()
    .UseRecommendedSerializerSettings()
    .UseSqlServerStorage(connectionString));
builder.Services.AddHangfireServer();

builder.Services.AddScoped<NightlyPremiumCalculationJob>();
builder.Services.AddScoped<MonthlyRegulatoryReportJob>();

builder.Services.AddHealthChecks().AddDbContextCheck<PolicyDbContext>();

var app = builder.Build();

// Hangfire needs SQL Server up before it can create its schema — in docker-compose
// this host may win the race against the database container.
await WaitForDatabaseAsync(connectionString, app.Logger);

// Dashboard: open in Development (local/docker-compose); everywhere else Hangfire's
// default LocalRequestsOnly filter applies — the ingress never routes here anyway.
var dashboardOptions = new DashboardOptions();
if (app.Environment.IsDevelopment())
{
    dashboardOptions.Authorization = [new Portal.Jobs.AllowAllDashboardAuthorizationFilter()];
}

app.UseHangfireDashboard("/hangfire", dashboardOptions);
app.MapHealthChecks("/healthz");

// Cron in UTC. Nightly run must finish before brokers start their day (pitfall #10).
RecurringJob.AddOrUpdate<NightlyPremiumCalculationJob>(
    "nightly-premium-calculation", j => j.RunAsync(CancellationToken.None), "0 2 * * *");

RecurringJob.AddOrUpdate<MonthlyRegulatoryReportJob>(
    "monthly-regulatory-report", j => j.RunAsync(CancellationToken.None), "0 4 1 * *");

app.Run();

static async Task WaitForDatabaseAsync(string connectionString, ILogger logger)
{
    const int maxAttempts = 30;
    for (var attempt = 1; ; attempt++)
    {
        try
        {
            await using var connection = new SqlConnection(connectionString);
            await connection.OpenAsync();
            return;
        }
        catch (SqlException) when (attempt < maxAttempts)
        {
            logger.LogWarning("Database not reachable yet (attempt {Attempt}/{Max}); retrying in 2s",
                attempt, maxAttempts);
            await Task.Delay(TimeSpan.FromSeconds(2));
        }
    }
}
