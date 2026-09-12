using System.Security.Claims;

namespace Policy.Api.Auth;

public static class PortalClaimTypes
{
    public const string CustomerId = "customer_id";
}

public static class PortalRoles
{
    public const string Broker = "Broker";
    public const string Underwriter = "Underwriter";
    public const string Customer = "Customer";
}

public interface ICurrentUser
{
    string Name { get; }
    bool IsCustomer { get; }

    /// <summary>Set when the caller is a customer — used for resource scoping.</summary>
    Guid? CustomerId { get; }
}

public class HttpCurrentUser(IHttpContextAccessor accessor) : ICurrentUser
{
    private ClaimsPrincipal? Principal => accessor.HttpContext?.User;

    public string Name => Principal?.Identity?.Name ?? "anonymous";

    public bool IsCustomer =>
        Principal?.IsInRole(PortalRoles.Customer) == true
        && Principal.IsInRole(PortalRoles.Broker) != true;

    public Guid? CustomerId =>
        Guid.TryParse(Principal?.FindFirstValue(PortalClaimTypes.CustomerId), out var id) ? id : null;
}
