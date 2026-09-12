namespace InvoiceFactoring.Domain.Enums;

public enum AdvanceStatus
{
    Pending = 0,    // created, payout not yet settled
    Disbursed = 1,  // funds reached the borrower (Stripe payout.paid)
    Repaid = 2,     // debtor paid the invoice, advance reconciled
    Defaulted = 3   // debtor failed to pay within the grace period
}
