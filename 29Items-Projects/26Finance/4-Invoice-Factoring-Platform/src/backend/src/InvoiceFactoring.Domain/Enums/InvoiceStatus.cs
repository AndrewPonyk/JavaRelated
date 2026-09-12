namespace InvoiceFactoring.Domain.Enums;

/// <summary>Lifecycle of a submitted invoice. See ARCHITECTURE.md §2.3 state machine.</summary>
public enum InvoiceStatus
{
    Submitted = 0,
    UnderReview = 1,
    Approved = 2,
    Declined = 3,
    Disbursing = 4,
    Outstanding = 5,
    Repaid = 6,
    Defaulted = 7
}
