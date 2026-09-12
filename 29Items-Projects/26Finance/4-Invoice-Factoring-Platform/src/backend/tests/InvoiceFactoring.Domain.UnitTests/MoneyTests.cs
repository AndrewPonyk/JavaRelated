using FluentAssertions;
using InvoiceFactoring.Domain.Exceptions;
using InvoiceFactoring.Domain.ValueObjects;
using Xunit;

namespace InvoiceFactoring.Domain.UnitTests;

public class MoneyTests
{
    [Fact]
    public void Constructor_RoundsToTwoDecimals()
    {
        new Money(10.005m).Amount.Should().Be(10.00m);   // banker's rounding
        new Money(10.015m).Amount.Should().Be(10.02m);
    }

    [Fact]
    public void Add_SameCurrency_Sums()
    {
        new Money(10m).Add(new Money(5.50m)).Amount.Should().Be(15.50m);
    }

    [Fact]
    public void Add_DifferentCurrency_Throws()
    {
        var act = () => new Money(10m, "USD").Add(new Money(10m, "EUR"));

        act.Should().Throw<DomainException>().WithMessage("*mismatch*");
    }

    [Fact]
    public void Multiply_ScalesAmount()
    {
        new Money(100m).Multiply(0.85m).Amount.Should().Be(85m);
    }

    [Fact]
    public void Constructor_InvalidCurrency_Throws()
    {
        var act = () => new Money(1m, "US");

        act.Should().Throw<DomainException>();
    }
}
