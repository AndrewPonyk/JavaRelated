using FluentAssertions;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Xunit;

namespace Policy.Api.Tests;

public class CustomerTests
{
    private static readonly DateOnly Today = new(2026, 6, 12);

    [Fact]
    public void Create_NormalizesNamesAndEmail()
    {
        var customer = Customer.Create("  Ada ", " Lovelace ", " Ada.Lovelace@Example.COM ", new DateOnly(1990, 12, 10), Today);

        customer.FirstName.Should().Be("Ada");
        customer.LastName.Should().Be("Lovelace");
        customer.Email.Should().Be("ada.lovelace@example.com");
        customer.FullName.Should().Be("Ada Lovelace");
    }

    [Fact]
    public void Create_Under18_Throws()
    {
        var act = () => Customer.Create("Kid", "Young", "kid@example.com", Today.AddYears(-18).AddDays(1), Today);

        act.Should().Throw<DomainException>().WithMessage("*18*");
    }

    [Fact]
    public void Create_Exactly18Today_Succeeds()
    {
        var customer = Customer.Create("Just", "Adult", "adult@example.com", Today.AddYears(-18), Today);

        customer.AgeOn(Today).Should().Be(18);
    }

    [Theory]
    [InlineData(2000, 6, 13, 25)] // birthday tomorrow — still 25
    [InlineData(2000, 6, 12, 26)] // birthday today — 26
    [InlineData(2000, 6, 11, 26)] // birthday yesterday — 26
    public void AgeOn_HandlesBirthdayBoundaries(int year, int month, int day, int expectedAge)
    {
        var customer = Customer.Create("B", "Day", "bday@example.com", new DateOnly(year, month, day), Today);

        customer.AgeOn(Today).Should().Be(expectedAge);
    }
}
