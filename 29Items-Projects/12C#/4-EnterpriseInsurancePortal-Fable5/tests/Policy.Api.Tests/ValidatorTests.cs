using FluentAssertions;
using Policy.Api.Models;
using Policy.Api.Validators;
using Xunit;

namespace Policy.Api.Tests;

public class ValidatorTests
{
    [Fact]
    public void CreateCustomer_Valid_Passes()
    {
        var result = new CreateCustomerRequestValidator().Validate(
            new CreateCustomerRequest("Ada", "Lovelace", "ada@example.com", new DateOnly(1990, 1, 1)));

        result.IsValid.Should().BeTrue();
    }

    [Theory]
    [InlineData("", "Lovelace", "ada@example.com")]
    [InlineData("Ada", "", "ada@example.com")]
    [InlineData("Ada", "Lovelace", "not-an-email")]
    public void CreateCustomer_Invalid_Fails(string first, string last, string email)
    {
        var result = new CreateCustomerRequestValidator().Validate(
            new CreateCustomerRequest(first, last, email, new DateOnly(1990, 1, 1)));

        result.IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData("CA", true)]
    [InlineData("ny", true)]
    [InlineData("C", false)]
    [InlineData("CAL", false)]
    [InlineData("C1", false)]
    public void CreateQuote_StateCode_MustBeTwoLetters(string state, bool expected)
    {
        var result = new CreateQuoteRequestValidator().Validate(
            new CreateQuoteRequest(Guid.NewGuid(), "BRK-001", "AUTO-STD", state, null));

        result.IsValid.Should().Be(expected);
    }

    [Fact]
    public void BindPolicy_EmptyQuoteId_Fails()
    {
        var result = new BindPolicyRequestValidator().Validate(new BindPolicyRequest(Guid.Empty, "BRK-001"));

        result.IsValid.Should().BeFalse();
    }

    [Fact]
    public void FileClaim_NegativeAmount_Fails()
    {
        var result = new FileClaimRequestValidator().Validate(new FileClaimRequest("desc", -1m));

        result.IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData("review", null, true)]
    [InlineData("approve", 100, true)]
    [InlineData("approve", null, false)] // approving requires an amount
    [InlineData("escalate", null, false)] // unknown action
    public void UpdateClaimStatus_ActionRules(string action, int? amount, bool expected)
    {
        var result = new UpdateClaimStatusRequestValidator().Validate(
            new UpdateClaimStatusRequest(action, amount));

        result.IsValid.Should().Be(expected);
    }
}
