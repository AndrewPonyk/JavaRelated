using FluentValidation;

namespace InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;

/// <summary>Input validation for <see cref="SubmitInvoiceCommand"/>; runs in the pipeline
/// before the handler (see <c>ValidationBehavior</c>).</summary>
public sealed class SubmitInvoiceCommandValidator : AbstractValidator<SubmitInvoiceCommand>
{
    public SubmitInvoiceCommandValidator()
    {
        RuleFor(x => x.CompanyId).NotEmpty();

        RuleFor(x => x.DebtorName)
            .NotEmpty().MaximumLength(200);

        RuleFor(x => x.DebtorTaxId)
            .NotEmpty().MaximumLength(32);

        RuleFor(x => x.Amount)
            .GreaterThan(0)
            .LessThanOrEqualTo(5_000_000m).WithMessage("Invoice exceeds the maximum factorable amount.");

        RuleFor(x => x.Currency)
            .NotEmpty().Length(3).WithMessage("Currency must be a 3-letter ISO-4217 code.");

        RuleFor(x => x.DueDate)
            .GreaterThan(x => x.IssueDate).WithMessage("Due date must be after the issue date.");

        RuleFor(x => x.IssueDate)
            .LessThanOrEqualTo(_ => DateOnly.FromDateTime(DateTime.UtcNow))
            .WithMessage("Issue date cannot be in the future.");
    }
}
