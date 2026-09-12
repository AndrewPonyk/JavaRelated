using Hangfire.Dashboard;

namespace Portal.Jobs;

/// <summary>
/// Grants dashboard access to any request. Registered ONLY in the Development
/// environment (see Program.cs); other environments keep Hangfire's default
/// local-requests-only filter.
/// </summary>
public class AllowAllDashboardAuthorizationFilter : IDashboardAuthorizationFilter
{
    public bool Authorize(DashboardContext context) => true;
}
