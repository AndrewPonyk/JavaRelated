using InvoiceFactoring.Application.Abstractions.Banking;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace InvoiceFactoring.Infrastructure.Banking.Plaid;

/// <summary>
/// Plaid adapter for the <see cref="IBankDataProvider"/> port. Pulls the borrower's recent
/// transactions for the linked Item and derives the cash-flow features the model consumes.
/// </summary>
public sealed class PlaidBankDataProvider : IBankDataProvider
{
    private readonly PlaidOptions _options;
    private readonly ILogger<PlaidBankDataProvider> _logger;

    public PlaidBankDataProvider(IOptions<PlaidOptions> options, ILogger<PlaidBankDataProvider> logger)
    {
        _options = options.Value;
        _logger = logger;
    }

    public Task<BankCashFlowSnapshot> GetCashFlowAsync(string plaidItemId, CancellationToken ct = default)
    {
        _logger.LogInformation("Fetching Plaid cash-flow for item {ItemId} ({Env}).",
            plaidItemId, _options.Environment);

        // TODO: exchange the stored access_token (from Key Vault, keyed by plaidItemId) and call
        //   Going.Plaid: TransactionsSyncAsync / AccountsBalanceGetAsync, then aggregate to:
        //     - average monthly inflow / outflow over the trailing 90 days
        //     - average daily balance
        //     - account tenure
        //   Handle ITEM_LOGIN_REQUIRED by surfacing a re-auth requirement (TECH-NOTES §3.6).
        var snapshot = new BankCashFlowSnapshot(
            MonthlyInflow: 0,
            MonthlyOutflow: 0,
            AverageDailyBalance: 0,
            AccountTenureMonths: 0);

        return Task.FromResult(snapshot);
    }
}
