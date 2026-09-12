using InvoiceFactoring.Domain.Common;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.Events;
using InvoiceFactoring.Domain.Exceptions;
using InvoiceFactoring.Domain.ValueObjects;

namespace InvoiceFactoring.Domain.Entities;

/// <summary>
/// Aggregate root for the factoring lifecycle of a single customer invoice.
/// All state transitions are guarded here so an invoice can never reach an illegal
/// state (e.g. be disbursed twice). See ARCHITECTURE §2.3 for the state machine.
/// </summary>
public class Invoice : AggregateRoot
{
    public Guid CompanyId { get; private set; }

    public string DebtorName { get; private set; } = null!;
    public string DebtorTaxId { get; private set; } = null!;

    public Money FaceValue { get; private set; }
    public DateOnly IssueDate { get; private set; }
    public DateOnly DueDate { get; private set; }

    public InvoiceStatus Status { get; private set; }
    public string? DocumentUri { get; private set; }   // Blob Storage reference to the PDF

    public Guid? CreditAssessmentId { get; private set; }
    public CreditAssessment? Assessment { get; private set; }
    public string? DeclineReason { get; private set; }

    private Invoice() { } // EF Core

    /// <summary>Factory: validate business rules and create a Submitted invoice.</summary>
    public static Invoice Submit(
        Guid companyId,
        string debtorName,
        string debtorTaxId,
        Money faceValue,
        DateOnly issueDate,
        DateOnly dueDate,
        string? documentUri = null)
    {
        if (companyId == Guid.Empty)
            throw new DomainException("CompanyId is required.");
        if (string.IsNullOrWhiteSpace(debtorName))
            throw new DomainException("Debtor name is required.");
        if (!faceValue.IsPositive)
            throw new DomainException("Invoice face value must be positive.");
        if (dueDate <= issueDate)
            throw new DomainException("Due date must be after the issue date.");

        var invoice = new Invoice
        {
            CompanyId = companyId,
            DebtorName = debtorName.Trim(),
            DebtorTaxId = debtorTaxId.Trim(),
            FaceValue = faceValue,
            IssueDate = issueDate,
            DueDate = dueDate,
            DocumentUri = documentUri,
            Status = InvoiceStatus.Submitted
        };

        invoice.Raise(new InvoiceSubmittedEvent(invoice.Id, companyId));
        return invoice;
    }

    public void MarkUnderReview()
    {
        EnsureStatus(InvoiceStatus.Submitted);
        Status = InvoiceStatus.UnderReview;
        Touch();
    }

    /// <summary>Apply the underwriting result: approve with an offer, or decline.</summary>
    public void ApplyAssessment(CreditAssessment assessment)
    {
        EnsureStatus(InvoiceStatus.Submitted, InvoiceStatus.UnderReview);
        if (assessment.InvoiceId != Id)
            throw new DomainException("Assessment does not belong to this invoice.");

        Assessment = assessment;
        CreditAssessmentId = assessment.Id;

        if (assessment.IsApproved)
        {
            Status = InvoiceStatus.Approved;
        }
        else
        {
            Status = InvoiceStatus.Declined;
            DeclineReason = "Default risk above acceptance threshold.";
        }
        Touch();
    }

    public void Decline(string reason)
    {
        EnsureStatus(InvoiceStatus.Submitted, InvoiceStatus.UnderReview, InvoiceStatus.Approved);
        Status = InvoiceStatus.Declined;
        DeclineReason = reason;
        Touch();
    }

    /// <summary>Borrower accepts the offer → we begin disbursing funds via Stripe.</summary>
    public void AcceptOffer()
    {
        EnsureStatus(InvoiceStatus.Approved);
        Status = InvoiceStatus.Disbursing;
        Touch();
    }

    /// <summary>Stripe payout settled → the advance is now outstanding against the debtor.</summary>
    public void MarkOutstanding()
    {
        EnsureStatus(InvoiceStatus.Disbursing);
        Status = InvoiceStatus.Outstanding;
        Touch();
    }

    public void MarkRepaid()
    {
        EnsureStatus(InvoiceStatus.Outstanding);
        Status = InvoiceStatus.Repaid;
        Touch();
    }

    public void MarkDefaulted()
    {
        EnsureStatus(InvoiceStatus.Outstanding);
        Status = InvoiceStatus.Defaulted;
        Touch();
    }

    private void EnsureStatus(params InvoiceStatus[] allowed)
    {
        if (Array.IndexOf(allowed, Status) < 0)
            throw new DomainException(
                $"Operation not allowed while invoice is '{Status}'. Expected one of: {string.Join(", ", allowed)}.");
    }
}
