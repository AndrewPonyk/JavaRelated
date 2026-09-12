using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace Policy.Api.Auth;

/// <summary>
/// Header-driven authentication used ONLY when no OIDC authority is configured
/// (local development and integration tests). Production config always sets
/// Identity:Authority, which switches the API to real JWT bearer validation —
/// this handler is then never registered.
///
/// Headers: X-Dev-Role (default Broker), X-Dev-User, X-Dev-CustomerId.
/// </summary>
public class DevAuthenticationHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "Dev";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var role = HeaderOrDefault("X-Dev-Role", PortalRoles.Broker);
        var user = HeaderOrDefault("X-Dev-User", "dev-broker");

        var claims = new List<Claim>
        {
            new(ClaimTypes.Name, user),
            new(ClaimTypes.Role, role),
        };

        var customerId = HeaderOrDefault("X-Dev-CustomerId", string.Empty);
        if (Guid.TryParse(customerId, out var parsed))
        {
            claims.Add(new Claim(PortalClaimTypes.CustomerId, parsed.ToString()));
        }

        var identity = new ClaimsIdentity(claims, SchemeName);
        var ticket = new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName);
        return Task.FromResult(AuthenticateResult.Success(ticket));
    }

    private string HeaderOrDefault(string header, string fallback)
    {
        var value = Request.Headers[header].ToString();
        return string.IsNullOrWhiteSpace(value) ? fallback : value;
    }
}
