using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace Policy.Infrastructure;

/// <summary>Used only by `dotnet ef` at design time (migration add/script).</summary>
public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<PolicyDbContext>
{
    public PolicyDbContext CreateDbContext(string[] args)
    {
        var options = new DbContextOptionsBuilder<PolicyDbContext>()
            .UseSqlServer("Server=localhost,1433;Database=InsurancePortal;Integrated Security=false;TrustServerCertificate=true")
            .Options;
        return new PolicyDbContext(options);
    }
}
