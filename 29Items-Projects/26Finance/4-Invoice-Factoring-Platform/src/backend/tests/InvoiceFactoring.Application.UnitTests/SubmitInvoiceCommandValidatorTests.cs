using FluentAssertions;
using InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class SubmitInvoiceCommandValidatorTests
{
    private readonly SubmitInvoiceCommandValidator _validator = new();

    private static SubmitInvoiceCommand Valid() => new(
        Guid.NewGuid(), "Acme Corp", "12-3456789", 10_000m, "USD",
        new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1), null);

    [Fact]
    public void Valid_PassesValidation()
    {
        _validator.Validate(Valid()).IsValid.Should().BeTrue();
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-100)]
    public void NonPositiveAmount_Fails(decimal amount)
    {
        var result = _validator.Validate(Valid() with { Amount = amount });
        result.IsValid.Should().BeFalse();
        result.Errors.Should().Contain(e => e.PropertyName == nameof(SubmitInvoiceCommand.Amount));
    }

    [Fact]
    public void DueDateBeforeIssueDate_Fails()
    {
        var result = _validator.Validate(Valid() with
        {
            IssueDate = new DateOnly(2026, 3, 1),
            DueDate = new DateOnly(2026, 1, 1)
        });
        result.IsValid.Should().BeFalse();
    }

    [Fact]
    public void InvalidCurrencyLength_Fails()
    {
        _validator.Validate(Valid() with { Currency = "US" }).IsValid.Should().BeFalse();
    }

    [Fact]
    public void EmptyDebtorName_Fails()
    {
        _validator.Validate(Valid() with { DebtorName = "" }).IsValid.Should().BeFalse();
    }
}
