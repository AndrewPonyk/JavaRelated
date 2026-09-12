using Microsoft.EntityFrameworkCore;
using Policy.Domain.Entities;

namespace Policy.Infrastructure;

public class PolicyDbContext(DbContextOptions<PolicyDbContext> options) : DbContext(options)
{
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<Quote> Quotes => Set<Quote>();
    public DbSet<InsurancePolicy> Policies => Set<InsurancePolicy>();
    public DbSet<Claim> Claims => Set<Claim>();
    public DbSet<OutboxMessage> OutboxMessages => Set<OutboxMessage>();
    public DbSet<AuditEntry> AuditEntries => Set<AuditEntry>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Customer>(e =>
        {
            e.Property(c => c.FirstName).HasMaxLength(100);
            e.Property(c => c.LastName).HasMaxLength(100);
            e.Property(c => c.Email).HasMaxLength(320);
            e.HasIndex(c => c.Email).IsUnique();
            e.Ignore(c => c.FullName);
        });

        modelBuilder.Entity<Quote>(e =>
        {
            e.Property(q => q.BrokerId).HasMaxLength(50);
            e.Property(q => q.ProductCode).HasMaxLength(30);
            e.Property(q => q.StateCode).HasMaxLength(2);
            e.Property(q => q.RateTableVersion).HasMaxLength(50);
            e.Property(q => q.Premium).HasPrecision(18, 2);
            e.Property(q => q.Status).HasConversion<string>().HasMaxLength(20);
            e.HasIndex(q => new { q.BrokerId, q.Status });
            e.HasOne(q => q.Customer).WithMany(c => c.Quotes).HasForeignKey(q => q.CustomerId);
        });

        modelBuilder.Entity<InsurancePolicy>(e =>
        {
            e.ToTable("Policies");
            e.Property(p => p.PolicyNumber).HasMaxLength(30);
            e.HasIndex(p => p.PolicyNumber).IsUnique();
            e.Property(p => p.AnnualPremium).HasPrecision(18, 2);
            e.Property(p => p.Status).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.CancellationReason).HasMaxLength(500);
            e.HasOne(p => p.Customer).WithMany(c => c.Policies).HasForeignKey(p => p.CustomerId)
                .OnDelete(DeleteBehavior.Restrict);
            e.HasOne(p => p.Quote).WithOne().HasForeignKey<InsurancePolicy>(p => p.QuoteId)
                .OnDelete(DeleteBehavior.Restrict);
            e.HasIndex(p => p.Status);        // nightly recalculation scans active policies
            e.HasIndex(p => p.EffectiveDate); // monthly regulatory report range query
        });

        modelBuilder.Entity<Claim>(e =>
        {
            e.Property(c => c.Description).HasMaxLength(2000);
            e.Property(c => c.ClaimedAmount).HasPrecision(18, 2);
            e.Property(c => c.ApprovedAmount).HasPrecision(18, 2);
            e.Property(c => c.Status).HasConversion<string>().HasMaxLength(20);
            e.HasOne(c => c.Policy).WithMany(p => p.Claims).HasForeignKey(c => c.PolicyId);
            e.HasIndex(c => c.FiledAtUtc); // monthly regulatory report range query
        });

        modelBuilder.Entity<OutboxMessage>(e =>
        {
            e.Property(m => m.EventType).HasMaxLength(200);
            e.Property(m => m.Key).HasMaxLength(100);
            e.HasIndex(m => m.ProcessedAtUtc).HasFilter("[ProcessedAtUtc] IS NULL");
        });

        modelBuilder.Entity<AuditEntry>(e =>
        {
            e.Property(a => a.Action).HasMaxLength(100);
            e.Property(a => a.EntityName).HasMaxLength(100);
            e.Property(a => a.Actor).HasMaxLength(200);
            e.HasIndex(a => new { a.EntityName, a.EntityId });
            e.HasIndex(a => a.OccurredAtUtc);
            e.HasIndex(a => new { a.Action, a.OccurredAtUtc }); // report audit reconciliation
        });
    }
}

/// <summary>
/// Transactional outbox row — written in the same transaction as the business change,
/// relayed to Kafka by <see cref="Outbox.OutboxProcessor"/>. See ARCHITECTURE.md §2.2.
/// </summary>
public class OutboxMessage
{
    public long Id { get; set; }
    public required string EventType { get; set; }

    /// <summary>Kafka partition key (PolicyId for policy events).</summary>
    public required string Key { get; set; }

    public required string Payload { get; set; } // JSON
    public DateTime CreatedAtUtc { get; set; }
    public DateTime? ProcessedAtUtc { get; set; }
}

/// <summary>
/// Immutable audit trail of every policy/claim mutation — required for monthly
/// regulatory reporting (ARCHITECTURE.md §2.5).
/// </summary>
public class AuditEntry
{
    public long Id { get; set; }
    public required string Action { get; set; }
    public required string EntityName { get; set; }
    public Guid EntityId { get; set; }
    public required string Actor { get; set; }
    public string? Details { get; set; }
    public DateTime OccurredAtUtc { get; set; }
}
