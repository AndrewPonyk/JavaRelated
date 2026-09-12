namespace InvoiceFactoring.Application.Abstractions.Banking;

/// <summary>
/// Abstraction over the banking-data provider (Plaid). Returns the cash-flow features
/// the credit model needs, derived from the borrower's linked account transactions.
/// </summary>
public interface IBankDataProvider
{
    Task<BankCashFlowSnapshot> GetCashFlowAsync(string plaidItemId, CancellationToken ct = default);
}

public sealed record BankCashFlowSnapshot(
    double MonthlyInflow,
    double MonthlyOutflow,
    double AverageDailyBalance,
    int AccountTenureMonths);
