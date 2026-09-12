using InvoiceFactoring.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace InvoiceFactoring.Infrastructure.Persistence.Configurations;

public sealed class CompanyConfiguration : IEntityTypeConfiguration<Company>
{
    public void Configure(EntityTypeBuilder<Company> builder)
    {
        builder.ToTable("Companies");
        builder.HasKey(c => c.Id);

        builder.Property(c => c.LegalName).HasMaxLength(200).IsRequired();

        // PII: in production this column is SQL Server Always Encrypted (ARCHITECTURE §2.5).
        builder.Property(c => c.TaxId).HasMaxLength(32).IsRequired();

        builder.Property(c => c.ContactEmail).HasMaxLength(256).IsRequired();
        builder.Property(c => c.StripeConnectedAccountId).HasMaxLength(64);
        builder.Property(c => c.PlaidItemId).HasMaxLength(64);

        builder.HasIndex(c => c.ContactEmail).IsUnique();
        builder.HasIndex(c => c.TaxId).IsUnique();
    }
}
