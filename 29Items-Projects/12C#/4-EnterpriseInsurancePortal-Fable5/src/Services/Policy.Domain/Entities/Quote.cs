using Policy.Domain.Exceptions;

namespace Policy.Domain.Entities;

public enum QuoteStatus { Draft, Issued, Bound, Expired, Declined }

public class Quote
{
    public const int ValidityDays = 30;

    public Guid Id { get; set; }
    public Guid CustomerId { get; set; }
    public Customer? Customer { get; set; }
    public required string BrokerId { get; set; }
    public required string ProductCode { get; set; }
    public required string StateCode { get; set; }

    /// <summary>Risk factors captured at quote time, serialized as JSON. Re-used by the nightly recalculation job.</summary>
    public string RiskFactorsJson { get; set; } = "{}";

    public decimal Premium { get; set; }
    public string RateTableVersion { get; set; } = string.Empty;
    public QuoteStatus Status { get; set; } = QuoteStatus.Draft;
    public DateTime IssuedAtUtc { get; set; }
    public DateTime ExpiresAtUtc { get; set; }

    public bool IsExpired(DateTime nowUtc) => nowUtc > ExpiresAtUtc;

    public static Quote Issue(
        Guid customerId,
        string brokerId,
        string productCode,
        string stateCode,
        string riskFactorsJson,
        decimal premium,
        string rateTableVersion,
        DateTime nowUtc)
    {
        if (premium <= 0)
        {
            throw new DomainException("A quote premium must be positive.");
        }

        return new Quote
        {
            Id = Guid.NewGuid(),
            CustomerId = customerId,
            BrokerId = brokerId,
            ProductCode = productCode,
            StateCode = stateCode.ToUpperInvariant(),
            RiskFactorsJson = riskFactorsJson,
            Premium = premium,
            RateTableVersion = rateTableVersion,
            Status = QuoteStatus.Issued,
            IssuedAtUtc = nowUtc,
            ExpiresAtUtc = nowUtc.AddDays(ValidityDays),
        };
    }

    public void Decline()
    {
        if (Status != QuoteStatus.Issued)
        {
            throw new InvalidStateTransitionException($"Only issued quotes can be declined (current: {Status}).");
        }

        Status = QuoteStatus.Declined;
    }

    public InsurancePolicy Bind(DateTime nowUtc)
    {
        if (Status != QuoteStatus.Issued)
        {
            throw new InvalidStateTransitionException($"Only issued quotes can be bound (current: {Status}).");
        }

        if (IsExpired(nowUtc))
        {
            Status = QuoteStatus.Expired;
            throw new InvalidStateTransitionException("Quote has expired and must be re-rated.");
        }

        Status = QuoteStatus.Bound;
        return new InsurancePolicy
        {
            Id = Guid.NewGuid(),
            QuoteId = Id,
            CustomerId = CustomerId,
            PolicyNumber = $"POL-{nowUtc:yyyyMMdd}-{Id.ToString("N")[..8].ToUpperInvariant()}",
            AnnualPremium = Premium,
            EffectiveDate = DateOnly.FromDateTime(nowUtc),
            ExpiryDate = DateOnly.FromDateTime(nowUtc.AddYears(1)),
            Status = PolicyStatus.Active,
        };
    }
}
