using InvoiceFactoring.Domain.Common;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.Exceptions;
using InvoiceFactoring.Domain.ValueObjects;

namespace InvoiceFactoring.Domain.Entities;

/// <summary>
/// The money-movement record for a funded invoice: how much was advanced, the fee taken,
/// the reserve withheld until the debtor pays, and the Stripe payout linkage.
/// </summary>
public class Advance : Entity
{
    public Guid InvoiceId { get; private set; }
    public Guid CompanyId { get; private set; }

    public Money GrossAdvance { get; private set; }   // faceValue * advanceRate
    public Money Fee { get; private set; }            // platform revenue
    public Money NetDisbursed { get; private set; }   // paid to borrower today
    public Money Reserve { get; private set; }        // faceValue − grossAdvance, released on repayment

    public AdvanceStatus Status { get; private set; }
    public string? StripePayoutId { get; private set; }

    private Advance() { } // EF Core

    /// <summary>Create the advance from an approved invoice and its assessment pricing.</summary>
    public static Advance Create(Invoice invoice)
    {
        if (invoice.Assessment is null || !invoice.Assessment.IsApproved)
            throw new DomainException("Cannot create an advance for an unapproved invoice.");

        var face = invoice.FaceValue;
        var assessment = invoice.Assessment;
        var gross = assessment.GrossAdvance(face);

        return new Advance
        {
            InvoiceId = invoice.Id,
            CompanyId = invoice.CompanyId,
            GrossAdvance = gross,
            Fee = assessment.Fee(face),
            NetDisbursed = assessment.NetDisbursement(face),
            Reserve = face.Subtract(gross),
            Status = AdvanceStatus.Pending
        };
    }

    public void AttachPayout(string stripePayoutId)
    {
        StripePayoutId = stripePayoutId;
        Touch();
    }

    public void MarkDisbursed()
    {
        if (Status != AdvanceStatus.Pending)
            throw new DomainException($"Advance cannot be disbursed from status '{Status}'.");
        Status = AdvanceStatus.Disbursed;
        Touch();
    }

    public void MarkRepaid()
    {
        if (Status != AdvanceStatus.Disbursed)
            throw new DomainException($"Advance cannot be repaid from status '{Status}'.");
        Status = AdvanceStatus.Repaid;
        Touch();
    }
}
