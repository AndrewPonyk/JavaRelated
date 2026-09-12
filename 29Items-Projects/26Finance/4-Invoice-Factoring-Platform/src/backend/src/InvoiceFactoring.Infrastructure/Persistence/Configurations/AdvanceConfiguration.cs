using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.ValueObjects;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace InvoiceFactoring.Infrastructure.Persistence.Configurations;

public sealed class AdvanceConfiguration : IEntityTypeConfiguration<Advance>
{
    public void Configure(EntityTypeBuilder<Advance> builder)
    {
        builder.ToTable("Advances");
        builder.HasKey(a => a.Id);

        builder.Property(a => a.Status)
            .HasConversion<string>()
            .HasMaxLength(20);

        builder.Property(a => a.StripePayoutId).HasMaxLength(64);

        MapMoney(builder, a => a.GrossAdvance, "GrossAdvance");
        MapMoney(builder, a => a.Fee, "Fee");
        MapMoney(builder, a => a.NetDisbursed, "NetDisbursed");
        MapMoney(builder, a => a.Reserve, "Reserve");

        builder.HasIndex(a => a.InvoiceId).IsUnique();
        builder.HasIndex(a => a.StripePayoutId);
        builder.HasIndex(a => a.Status);
    }

    private static void MapMoney(
        EntityTypeBuilder<Advance> builder,
        System.Linq.Expressions.Expression<Func<Advance, Money>> property,
        string prefix)
    {
        builder.ComplexProperty(property, money =>
        {
            money.Property(m => m.Amount).HasColumnName($"{prefix}Amount").HasPrecision(18, 2);
            money.Property(m => m.Currency).HasColumnName($"{prefix}Currency").HasMaxLength(3);
        });
    }
}
