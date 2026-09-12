using InvoiceFactoring.Domain.Exceptions;

namespace InvoiceFactoring.Domain.ValueObjects;

/// <summary>
/// Money as an immutable value object. Uses <see cref="decimal"/> (never double) and
/// guards currency so you can't accidentally add USD to EUR. Amounts are rounded to the
/// currency's minor unit (2 dp here) using banker's rounding.
/// </summary>
public readonly record struct Money
{
    public decimal Amount { get; }
    public string Currency { get; }

    public Money(decimal amount, string currency = "USD")
    {
        if (string.IsNullOrWhiteSpace(currency) || currency.Length != 3)
            throw new DomainException($"Invalid currency code '{currency}'. Expected ISO-4217 (e.g. USD).");

        Currency = currency.ToUpperInvariant();
        Amount = Math.Round(amount, 2, MidpointRounding.ToEven);
    }

    public static Money Zero(string currency = "USD") => new(0m, currency);

    public Money Add(Money other)
    {
        EnsureSameCurrency(other);
        return new Money(Amount + other.Amount, Currency);
    }

    public Money Subtract(Money other)
    {
        EnsureSameCurrency(other);
        return new Money(Amount - other.Amount, Currency);
    }

    /// <summary>Scales the amount by a rate (e.g. advance rate 0.85m). Used by pricing.</summary>
    public Money Multiply(decimal factor) => new(Amount * factor, Currency);

    public bool IsPositive => Amount > 0m;

    private void EnsureSameCurrency(Money other)
    {
        if (Currency != other.Currency)
            throw new DomainException($"Currency mismatch: {Currency} vs {other.Currency}.");
    }

    public override string ToString() => $"{Amount:0.00} {Currency}";
}
