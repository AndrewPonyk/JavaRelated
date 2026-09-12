using System.Threading.RateLimiting;
using FluentValidation;
using FluentValidation.AspNetCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.ResponseCompression;
using Microsoft.EntityFrameworkCore;
using Policy.Api.Auth;
using Policy.Api.Middleware;
using Policy.Api.Outbox;
using Policy.Api.Rating;
using Policy.Api.Services;
using Policy.Infrastructure;
using Policy.Infrastructure.Outbox;
using Portal.Shared.Kafka;
using Rating.Grpc;

var builder = WebApplication.CreateBuilder(args);

// ── MVC + validation ─────────────────────────────────────────────────────
builder.Services.AddControllers();
builder.Services.AddFluentValidationAutoValidation();
builder.Services.AddValidatorsFromAssemblyContaining<Program>();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// ── Persistence ──────────────────────────────────────────────────────────
builder.Services.AddDbContextFactory<PolicyDbContext>(o =>
    o.UseSqlServer(builder.Configuration.GetConnectionString("PolicyDb")));

// ── Application services ─────────────────────────────────────────────────
builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUser, HttpCurrentUser>();
builder.Services.AddScoped<ICustomerService, CustomerService>();
builder.Services.AddScoped<IQuoteService, QuoteService>();
builder.Services.AddScoped<IPolicyService, PolicyService>();
builder.Services.AddScoped<IClaimService, ClaimService>();

// ── Rating engine (gRPC) ─────────────────────────────────────────────────
var ratingAddress = builder.Configuration["Rating:GrpcAddress"];
if (!string.IsNullOrWhiteSpace(ratingAddress))
{
    builder.Services
        .AddGrpcClient<RatingService.RatingServiceClient>(o => o.Address = new Uri(ratingAddress));
    builder.Services.AddScoped<IRatingClient, GrpcRatingClient>();
}
else
{
    builder.Services.AddScoped<IRatingClient, UnconfiguredRatingClient>();
}

// ── Kafka + transactional outbox relay ───────────────────────────────────
builder.Services.AddKafkaEventPublishing(builder.Configuration);
builder.Services.AddScoped<OutboxProcessor>();
if (builder.Configuration.GetValue("Outbox:Enabled", true))
{
    builder.Services.AddHostedService<OutboxRelayHostedService>();
}

// ── AuthN / AuthZ ────────────────────────────────────────────────────────
// With an OIDC authority configured (staging/prod) the API validates real JWTs.
// Without one (local dev, integration tests) the header-driven Dev scheme is used.
var authority = builder.Configuration["Identity:Authority"];
if (!string.IsNullOrWhiteSpace(authority))
{
    builder.Services
        .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
        .AddJwtBearer(o =>
        {
            o.Authority = authority;
            o.TokenValidationParameters.ValidateAudience = false;
            o.TokenValidationParameters.RoleClaimType = "role";
            o.TokenValidationParameters.NameClaimType = "name";
        });
}
else
{
    builder.Services
        .AddAuthentication(DevAuthenticationHandler.SchemeName)
        .AddScheme<Microsoft.AspNetCore.Authentication.AuthenticationSchemeOptions, DevAuthenticationHandler>(
            DevAuthenticationHandler.SchemeName, null);
}

builder.Services.AddAuthorizationBuilder()
    .AddPolicy("Broker", p => p.RequireRole(PortalRoles.Broker, PortalRoles.Underwriter));

// ── CORS (React public portal) ───────────────────────────────────────────
var corsOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [];
builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.WithOrigins(corsOrigins).AllowAnyHeader().AllowAnyMethod()
        .WithExposedHeaders("X-Total-Count")));

// ── Rate limiting (per authenticated caller, falling back to client IP) ──
var permitLimit = builder.Configuration.GetValue("RateLimiting:PermitPerSecond", 100);
builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    o.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
        RateLimitPartition.GetFixedWindowLimiter(
            ctx.User.Identity?.Name
                ?? ctx.Connection.RemoteIpAddress?.ToString()
                ?? "anonymous",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = permitLimit,
                Window = TimeSpan.FromSeconds(1),
                QueueLimit = 0,
            }));
});

// ── Response compression (TLS terminates at the ingress) ─────────────────
builder.Services.AddResponseCompression(o =>
{
    o.EnableForHttps = true;
    o.MimeTypes = ResponseCompressionDefaults.MimeTypes.Concat(["application/problem+json"]);
});

builder.Services.AddHealthChecks()
    .AddDbContextCheck<PolicyDbContext>();

var app = builder.Build();

// Applies pending EF migrations on startup when enabled (docker-compose / dev convenience).
// CI/CD environments run migrations as a separate k8s Job instead (TECH-NOTES.md §3.1).
if (app.Configuration.GetValue("Database:MigrateOnStartup", false))
{
    using var scope = app.Services.CreateScope();
    var dbFactory = scope.ServiceProvider.GetRequiredService<IDbContextFactory<PolicyDbContext>>();
    using var db = dbFactory.CreateDbContext();
    db.Database.Migrate();
}

app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseResponseCompression();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseCors();
app.UseAuthentication();
app.UseRateLimiter();
app.UseAuthorization();
app.MapControllers();
app.MapHealthChecks("/healthz").DisableRateLimiting();

app.Run();

public partial class Program; // exposed for WebApplicationFactory in integration tests
