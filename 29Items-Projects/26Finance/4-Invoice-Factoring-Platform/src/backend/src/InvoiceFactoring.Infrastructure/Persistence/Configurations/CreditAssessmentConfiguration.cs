using InvoiceFactoring.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace InvoiceFactoring.Infrastructure.Persistence.Configurations;

public sealed class CreditAssessmentConfiguration : IEntityTypeConfiguration<CreditAssessment>
{
    public void Configure(EntityTypeBuilder<CreditAssessment> builder)
    {
        builder.ToTable("CreditAssessments");
        builder.HasKey(a => a.Id);

        builder.Property(a => a.RiskGrade)
            .HasConversion<string>()
            .HasMaxLength(2);

        builder.Property(a => a.AdvanceRate).HasPrecision(5, 4);
        builder.Property(a => a.DiscountFeeRate).HasPrecision(5, 4);
        builder.Property(a => a.ModelVersion).HasMaxLength(32);

        // Reason codes / feature attributions for adverse-action notices (ARCHITECTURE §2.5).
        builder.Property(a => a.ReasonCodesJson).HasColumnType("nvarchar(max)");

        builder.HasIndex(a => a.InvoiceId).IsUnique();
    }
}
