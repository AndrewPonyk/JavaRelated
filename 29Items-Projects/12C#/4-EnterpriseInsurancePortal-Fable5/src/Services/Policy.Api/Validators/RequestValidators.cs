using FluentValidation;
using Policy.Api.Models;

namespace Policy.Api.Validators;

public class CreateCustomerRequestValidator : AbstractValidator<CreateCustomerRequest>
{
    public CreateCustomerRequestValidator()
    {
        RuleFor(x => x.FirstName).NotEmpty().MaximumLength(100);
        RuleFor(x => x.LastName).NotEmpty().MaximumLength(100);
        RuleFor(x => x.Email).NotEmpty().EmailAddress().MaximumLength(320);
        RuleFor(x => x.DateOfBirth)
            .LessThan(DateOnly.FromDateTime(DateTime.UtcNow))
            .WithMessage("Date of birth must be in the past.");
    }
}

public class CreateQuoteRequestValidator : AbstractValidator<CreateQuoteRequest>
{
    public CreateQuoteRequestValidator()
    {
        RuleFor(x => x.CustomerId).NotEmpty();
        RuleFor(x => x.BrokerId).NotEmpty().MaximumLength(50);
        RuleFor(x => x.ProductCode).NotEmpty().MaximumLength(30);
        RuleFor(x => x.StateCode).NotEmpty().Length(2)
            .Matches("^[A-Za-z]{2}$").WithMessage("State code must be a two-letter code.");
        RuleForEach(x => x.RiskFactors!.Keys).MaximumLength(50).When(x => x.RiskFactors is not null);
    }
}

public class BindPolicyRequestValidator : AbstractValidator<BindPolicyRequest>
{
    public BindPolicyRequestValidator()
    {
        RuleFor(x => x.QuoteId).NotEmpty();
        RuleFor(x => x.BrokerId).NotEmpty().MaximumLength(50);
    }
}

public class CancelPolicyRequestValidator : AbstractValidator<CancelPolicyRequest>
{
    public CancelPolicyRequestValidator()
    {
        RuleFor(x => x.Reason).NotEmpty().MaximumLength(500);
    }
}

public class FileClaimRequestValidator : AbstractValidator<FileClaimRequest>
{
    public FileClaimRequestValidator()
    {
        RuleFor(x => x.Description).NotEmpty().MaximumLength(2000);
        RuleFor(x => x.ClaimedAmount).GreaterThan(0).LessThanOrEqualTo(10_000_000);
    }
}

public class UpdateClaimStatusRequestValidator : AbstractValidator<UpdateClaimStatusRequest>
{
    private static readonly string[] Actions = ["review", "approve", "reject", "pay"];

    public UpdateClaimStatusRequestValidator()
    {
        RuleFor(x => x.Action).NotEmpty()
            .Must(a => Actions.Contains(a, StringComparer.OrdinalIgnoreCase))
            .WithMessage("Action must be one of: review, approve, reject, pay.");
        RuleFor(x => x.ApprovedAmount).NotNull().GreaterThan(0)
            .When(x => string.Equals(x.Action, "approve", StringComparison.OrdinalIgnoreCase))
            .WithMessage("approvedAmount is required and must be positive when approving.");
    }
}
