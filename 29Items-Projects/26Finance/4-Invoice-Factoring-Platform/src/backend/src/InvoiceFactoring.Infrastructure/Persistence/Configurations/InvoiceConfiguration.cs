using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace InvoiceFactoring.Infrastructure.Persistence.Configurations;

public sealed class InvoiceConfiguration : IEntityTypeConfiguration<Invoice>
{
    public void Configure(EntityTypeBuilder<Invoice> builder)
    {
        builder.ToTable("Invoices");
        builder.HasKey(i => i.Id);

        builder.Property(i => i.DebtorName).HasMaxLength(200).IsRequired();
        builder.Property(i => i.DebtorTaxId).HasMaxLength(32).IsRequired();
        builder.Property(i => i.DocumentUri).HasMaxLength(1024);
        builder.Property(i => i.DeclineReason).HasMaxLength(500);

        // Enum stored as a readable string rather than a magic int.
        builder.Property(i => i.Status)
            .HasConversion<string>()
            .HasMaxLength(20);

        // Money value object → two columns (EF8 complex type).
        builder.ComplexProperty(i => i.FaceValue, money =>
        {
            money.Property(m => m.Amount).HasColumnName("FaceValueAmount").HasPrecision(18, 2);
            money.Property(m => m.Currency).HasColumnName("FaceValueCurrency").HasMaxLength(3);
        });

        // One-to-one with the assessment (FK lives on CreditAssessment.InvoiceId).
        builder.HasOne(i => i.Assessment)
            .WithOne()
            .HasForeignKey<CreditAssessment>(a => a.InvoiceId)
            .OnDelete(DeleteBehavior.Cascade);

        builder.HasIndex(i => new { i.CompanyId, i.Status });
        builder.HasIndex(i => i.Status);
    }
}
