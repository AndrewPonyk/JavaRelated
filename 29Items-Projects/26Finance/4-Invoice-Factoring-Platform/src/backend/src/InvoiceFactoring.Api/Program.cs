using System.Threading.RateLimiting;
using Azure.Monitor.OpenTelemetry.AspNetCore;
using InvoiceFactoring.Api.Middleware;
using InvoiceFactoring.Api.Workers;
using InvoiceFactoring.Application;
using InvoiceFactoring.Infrastructure;
using InvoiceFactoring.Infrastructure.Persistence;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.EntityFrameworkCore;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

// ── Logging (structured, with correlation/trace ids → App Insights) ─────────────────
builder.Host.UseSerilog((context, config) =>
    config.ReadFrom.Configuration(context.Configuration)
          .Enrich.FromLogContext());

// ── Secrets: in Azure, configuration is layered with Key Vault via Managed Identity ─
if (!builder.Environment.IsDevelopment())
{
    // TODO: builder.Configuration.AddAzureKeyVault(new Uri(kvUri), new DefaultAzureCredential());
}

// ── Application + Infrastructure layers ─────────────────────────────────────────────
builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

// ── Web API ─────────────────────────────────────────────────────────────────────────
builder.Services.AddControllers();
builder.Services.AddProblemDetails();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// CORS — restrict to the configured SPA origin(s).
var corsOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [];
builder.Services.AddCors(options =>
    options.AddDefaultPolicy(policy =>
    {
        if (corsOrigins.Length > 0)
            policy.WithOrigins(corsOrigins).AllowAnyHeader().AllowAnyMethod();
    }));

// Response compression (Brotli/Gzip), enabled over HTTPS.
builder.Services.AddResponseCompression(options => options.EnableForHttps = true);

// Per-client fixed-window rate limiting; rejected requests get 429.
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(context =>
        RateLimitPartition.GetFixedWindowLimiter(
            partitionKey: context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            factory: _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 100,
                Window = TimeSpan.FromMinutes(1)
            }));
});

// ── AuthN/AuthZ (Azure AD B2C / OIDC) ───────────────────────────────────────────────
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = builder.Configuration["Auth:Authority"];
        options.Audience = builder.Configuration["Auth:Audience"];
        options.RequireHttpsMetadata = !builder.Environment.IsDevelopment();
    });

builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("Borrower", p => p.RequireRole("Borrower"));
    options.AddPolicy("Underwriter", p => p.RequireRole("Underwriter", "Admin"));
});

// ── Async underwriting worker (consumes the Service Bus underwriting queue) ──────────
builder.Services.AddHostedService<UnderwritingWorker>();

// ── Observability + health probes ───────────────────────────────────────────────────
var appInsights = builder.Configuration["ApplicationInsights:ConnectionString"];
if (!string.IsNullOrWhiteSpace(appInsights))
{
    builder.Services.AddOpenTelemetry().UseAzureMonitor(o => o.ConnectionString = appInsights);
}

var healthChecks = builder.Services.AddHealthChecks();
var sqlConn = builder.Configuration.GetConnectionString("SqlServer");
if (!string.IsNullOrWhiteSpace(sqlConn))
    healthChecks.AddSqlServer(sqlConn, name: "sql", tags: new[] { "ready" });
var redisConn = builder.Configuration.GetConnectionString("Redis");
if (!string.IsNullOrWhiteSpace(redisConn))
    healthChecks.AddRedis(redisConn, name: "redis", tags: new[] { "ready" });

var app = builder.Build();

// Auto-apply EF Core migrations in Development for a frictionless local start.
// (Production applies migrations as a gated pre-deploy Job — see TECH-NOTES §3.3.)
if (app.Environment.IsDevelopment() &&
    !string.IsNullOrWhiteSpace(app.Configuration.GetConnectionString("SqlServer")))
{
    using var scope = app.Services.CreateScope();
    scope.ServiceProvider.GetRequiredService<AppDbContext>().Database.Migrate();
}

// ── Pipeline ────────────────────────────────────────────────────────────────────────
app.UseMiddleware<ExceptionHandlingMiddleware>();   // RFC 7807 ProblemDetails for all errors
app.UseSerilogRequestLogging();

// Security headers on every response.
app.Use(async (context, next) =>
{
    var headers = context.Response.Headers;
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "DENY";
    headers["Referrer-Policy"] = "strict-origin-when-cross-origin";
    await next();
});

app.UseResponseCompression();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}
else
{
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseCors();
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

// Liveness = process is up; readiness = dependencies reachable.
app.MapHealthChecks("/health/live", new HealthCheckOptions { Predicate = _ => false });
app.MapHealthChecks("/health/ready", new HealthCheckOptions { Predicate = h => h.Tags.Contains("ready") });

app.Run();

/// <summary>Exposed so integration tests can spin up the host via WebApplicationFactory.</summary>
public partial class Program { }
