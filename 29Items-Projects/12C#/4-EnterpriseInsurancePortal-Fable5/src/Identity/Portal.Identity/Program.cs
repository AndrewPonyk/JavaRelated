using Portal.Identity;

var builder = WebApplication.CreateBuilder(args);

// Token service for the portal ecosystem. In-memory stores carry local/dev and CI;
// the staging/prod rollout swaps in EF-backed configuration/operational stores, an
// ASP.NET Identity user store, Azure AD federation for internal brokers, and Key
// Vault-persisted signing keys (TECH-NOTES.md pitfall #7). Until interactive login
// ships (Phase 2 roadmap, PROJECT-PLAN.md), the APIs' Dev auth scheme covers local
// sign-in while this service issues client-credentials tokens.
builder.Services.AddIdentityServer(options => options.EmitStaticAudienceClaim = true)
    .AddInMemoryApiScopes(Config.ApiScopes)
    .AddInMemoryClients(Config.GetClients(builder.Configuration));

builder.Services.AddHealthChecks();

var app = builder.Build();

app.UseIdentityServer();
app.MapHealthChecks("/healthz");
app.MapGet("/", () => "Portal.Identity — OIDC discovery at /.well-known/openid-configuration");

app.Run();
