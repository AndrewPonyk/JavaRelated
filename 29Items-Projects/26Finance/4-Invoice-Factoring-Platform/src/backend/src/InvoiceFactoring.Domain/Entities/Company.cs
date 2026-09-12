using InvoiceFactoring.Domain.Common;
using InvoiceFactoring.Domain.Exceptions;

namespace InvoiceFactoring.Domain.Entities;

/// <summary>
/// An SMB borrower that submits invoices for factoring. Holds references to its
/// external payment/banking identities — never raw credentials (see ARCHITECTURE §2.5).
/// </summary>
public class Company : AggregateRoot
{
    public string LegalName { get; private set; } = null!;
    public string TaxId { get; private set; } = null!;          // stored Always-Encrypted
    public string ContactEmail { get; private set; } = null!;

    /// <summary>Stripe Connect account used to disburse advances to this borrower.</summary>
    public string? StripeConnectedAccountId { get; private set; }

    /// <summary>Plaid Item id; the access_token itself lives in Key Vault, not here.</summary>
    public string? PlaidItemId { get; private set; }

    public bool IsBankVerified { get; private set; }

    private Company() { } // EF Core

    public static Company Register(string legalName, string taxId, string contactEmail)
    {
        if (string.IsNullOrWhiteSpace(legalName))
            throw new DomainException("Company legal name is required.");
        if (string.IsNullOrWhiteSpace(taxId))
            throw new DomainException("Company tax id is required.");

        return new Company
        {
            LegalName = legalName.Trim(),
            TaxId = taxId.Trim(),
            ContactEmail = contactEmail.Trim().ToLowerInvariant()
        };
    }

    public void LinkBankAccount(string plaidItemId)
    {
        PlaidItemId = plaidItemId;
        IsBankVerified = true;
        Touch();
    }

    public void LinkStripeAccount(string connectedAccountId)
    {
        StripeConnectedAccountId = connectedAccountId;
        Touch();
    }
}
