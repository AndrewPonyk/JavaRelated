using InvoiceFactoring.Domain.Entities;

namespace InvoiceFactoring.Application.Features.Invoices.Dtos;

/// <summary>API-facing projection of an invoice, including the offer once underwritten.</summary>
public sealed record InvoiceDto(
    Guid Id,
    Guid CompanyId,
    string DebtorName,
    decimal FaceValue,
    string Currency,
    DateOnly IssueDate,
    DateOnly DueDate,
    string Status,
    OfferDto? Offer,
    string? DeclineReason,
    DateTimeOffset CreatedAt)
{
    public static InvoiceDto FromEntity(Invoice invoice)
    {
        OfferDto? offer = null;
        if (invoice.Assessment is { IsApproved: true } a)
        {
            offer = new OfferDto(
                RiskGrade: a.RiskGrade.ToString(),
                ProbabilityOfDefault: a.ProbabilityOfDefault,
                AdvanceRate: a.AdvanceRate,
                DiscountFeeRate: a.DiscountFeeRate,
                NetDisbursement: a.NetDisbursement(invoice.FaceValue).Amount,
                Fee: a.Fee(invoice.FaceValue).Amount);
        }

        return new InvoiceDto(
            invoice.Id,
            invoice.CompanyId,
            invoice.DebtorName,
            invoice.FaceValue.Amount,
            invoice.FaceValue.Currency,
            invoice.IssueDate,
            invoice.DueDate,
            invoice.Status.ToString(),
            offer,
            invoice.DeclineReason,
            invoice.CreatedAt);
    }
}

public sealed record OfferDto(
    string RiskGrade,
    double ProbabilityOfDefault,
    decimal AdvanceRate,
    decimal DiscountFeeRate,
    decimal NetDisbursement,
    decimal Fee);
