using Duende.IdentityServer.Models;

namespace Portal.Identity;

/// <summary>
/// Client and scope registrations. Secrets here are local-development values;
/// staging/prod override them via configuration backed by Azure Key Vault
/// (ARCHITECTURE.md §2.5) and replace the in-memory stores with EF stores.
/// </summary>
public static class Config
{
    public static IEnumerable<ApiScope> ApiScopes =>
    [
        new("policy.read"),
        new("policy.write"),
        new("claims.manage"),
        new("rating.calculate"),
    ];

    public static IEnumerable<Client> GetClients(IConfiguration configuration) =>
    [
        // Blazor Server broker portal (confidential, code flow)
        new()
        {
            ClientId = "portal-web",
            AllowedGrantTypes = GrantTypes.Code,
            ClientSecrets = { new Secret(configuration.GetValue("Clients:PortalWebSecret", "local-dev-secret")!.Sha256()) },
            RedirectUris = { configuration.GetValue("Clients:PortalWebRedirectUri", "http://localhost:5301/signin-oidc")! },
            AllowedScopes = { "openid", "profile", "policy.read", "policy.write", "claims.manage" },
        },
        // React customer portal (public, code + PKCE)
        new()
        {
            ClientId = "public-portal",
            AllowedGrantTypes = GrantTypes.Code,
            RequirePkce = true,
            RequireClientSecret = false,
            RedirectUris = { configuration.GetValue("Clients:PublicPortalRedirectUri", "http://localhost:5173/callback")! },
            AllowedCorsOrigins = { configuration.GetValue("Clients:PublicPortalOrigin", "http://localhost:5173")! },
            AllowedScopes = { "openid", "profile", "policy.read", "claims.manage" },
        },
        // Service-to-service: Policy.Api / Portal.Jobs → Rating.Grpc
        new()
        {
            ClientId = "policy-api",
            AllowedGrantTypes = GrantTypes.ClientCredentials,
            ClientSecrets = { new Secret(configuration.GetValue("Clients:PolicyApiSecret", "local-dev-secret")!.Sha256()) },
            AllowedScopes = { "rating.calculate" },
        },
    ];
}
