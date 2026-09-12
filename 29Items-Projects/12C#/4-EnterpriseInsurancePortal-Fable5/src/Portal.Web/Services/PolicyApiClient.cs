using System.Net;
using System.Net.Http.Json;

namespace Portal.Web.Services;

// Client-side mirrors of Policy.Api response/request shapes.
public record CustomerSummary(Guid Id, string FirstName, string LastName, string Email, DateOnly DateOfBirth);
public record QuoteSummary(
    Guid Id, Guid CustomerId, string BrokerId, string ProductCode, string StateCode,
    decimal Premium, string RateTableVersion, string Status, DateTime IssuedAtUtc, DateTime ExpiresAtUtc);
public record PolicySummary(
    Guid Id, string PolicyNumber, Guid CustomerId, decimal AnnualPremium,
    DateOnly EffectiveDate, DateOnly ExpiryDate, string Status);
public record PolicyDetailModel(
    Guid Id, string PolicyNumber, Guid CustomerId, string CustomerName, decimal AnnualPremium,
    DateOnly EffectiveDate, DateOnly ExpiryDate, string Status, string? CancellationReason,
    IReadOnlyList<ClaimSummary> Claims);
public record ClaimSummary(
    Guid Id, Guid PolicyId, string Description, decimal ClaimedAmount, decimal? ApprovedAmount,
    string Status, DateTime FiledAtUtc, DateTime? ResolvedAtUtc);

/// <summary>Raised with the server's ProblemDetails message so pages can show meaningful errors.</summary>
public class ApiException(HttpStatusCode statusCode, string message) : Exception(message)
{
    public HttpStatusCode StatusCode { get; } = statusCode;
}

public class PolicyApiClient(HttpClient http)
{
    // ── Customers ────────────────────────────────────────────────────────
    public Task<IReadOnlyList<CustomerSummary>> GetCustomersAsync(CancellationToken ct = default)
        => GetAsync<IReadOnlyList<CustomerSummary>>("api/v1/customers?pageSize=200", ct);

    public Task<CustomerSummary> CreateCustomerAsync(
        string firstName, string lastName, string email, DateOnly dateOfBirth, CancellationToken ct = default)
        => PostAsync<CustomerSummary>("api/v1/customers", new { firstName, lastName, email, dateOfBirth }, ct);

    // ── Quotes ───────────────────────────────────────────────────────────
    public Task<IReadOnlyList<QuoteSummary>> GetQuotesAsync(CancellationToken ct = default)
        => GetAsync<IReadOnlyList<QuoteSummary>>("api/v1/quotes?pageSize=200", ct);

    public Task<QuoteSummary> CreateQuoteAsync(
        Guid customerId, string brokerId, string productCode, string stateCode,
        Dictionary<string, string> riskFactors, CancellationToken ct = default)
        => PostAsync<QuoteSummary>("api/v1/quotes",
            new { customerId, brokerId, productCode, stateCode, riskFactors }, ct);

    public Task<QuoteSummary> DeclineQuoteAsync(Guid quoteId, CancellationToken ct = default)
        => PostAsync<QuoteSummary>($"api/v1/quotes/{quoteId}/decline", new { }, ct);

    // ── Policies ─────────────────────────────────────────────────────────
    public Task<IReadOnlyList<PolicySummary>> GetPoliciesAsync(CancellationToken ct = default)
        => GetAsync<IReadOnlyList<PolicySummary>>("api/v1/policies?pageSize=200", ct);

    public Task<PolicyDetailModel> GetPolicyAsync(Guid id, CancellationToken ct = default)
        => GetAsync<PolicyDetailModel>($"api/v1/policies/{id}", ct);

    public Task<PolicySummary> BindPolicyAsync(Guid quoteId, string brokerId, CancellationToken ct = default)
        => PostAsync<PolicySummary>("api/v1/policies", new { quoteId, brokerId }, ct);

    public async Task<PolicySummary> CancelPolicyAsync(Guid policyId, string reason, CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, $"api/v1/policies/{policyId}")
        {
            Content = JsonContent.Create(new { reason }),
        };
        var response = await http.SendAsync(request, ct);
        return await ReadAsync<PolicySummary>(response, ct);
    }

    // ── Claims ───────────────────────────────────────────────────────────
    public Task<IReadOnlyList<ClaimSummary>> GetClaimsAsync(Guid policyId, CancellationToken ct = default)
        => GetAsync<IReadOnlyList<ClaimSummary>>($"api/v1/policies/{policyId}/claims", ct);

    public Task<ClaimSummary> FileClaimAsync(
        Guid policyId, string description, decimal claimedAmount, CancellationToken ct = default)
        => PostAsync<ClaimSummary>($"api/v1/policies/{policyId}/claims", new { description, claimedAmount }, ct);

    public Task<ClaimSummary> UpdateClaimStatusAsync(
        Guid claimId, string action, decimal? approvedAmount = null, CancellationToken ct = default)
        => PostAsync<ClaimSummary>($"api/v1/claims/{claimId}/status", new { action, approvedAmount }, ct);

    // ── Plumbing ─────────────────────────────────────────────────────────
    private async Task<T> GetAsync<T>(string path, CancellationToken ct)
        => await ReadAsync<T>(await http.GetAsync(path, ct), ct);

    private async Task<T> PostAsync<T>(string path, object body, CancellationToken ct)
        => await ReadAsync<T>(await http.PostAsJsonAsync(path, body, ct), ct);

    private static async Task<T> ReadAsync<T>(HttpResponseMessage response, CancellationToken ct)
    {
        if (!response.IsSuccessStatusCode)
        {
            var detail = await TryReadProblemDetail(response, ct)
                ?? $"The request failed with status {(int)response.StatusCode}.";
            throw new ApiException(response.StatusCode, detail);
        }

        return await response.Content.ReadFromJsonAsync<T>(ct)
            ?? throw new ApiException(response.StatusCode, "The server returned an empty response.");
    }

    private static async Task<string?> TryReadProblemDetail(HttpResponseMessage response, CancellationToken ct)
    {
        try
        {
            var problem = await response.Content.ReadFromJsonAsync<ProblemPayload>(ct);
            if (problem?.Errors is { Count: > 0 } errors)
            {
                return string.Join(" ", errors.SelectMany(e => e.Value));
            }

            return problem?.Detail ?? problem?.Title;
        }
        catch (Exception ex) when (ex is System.Text.Json.JsonException or NotSupportedException)
        {
            return null;
        }
    }

    private sealed record ProblemPayload(string? Title, string? Detail, Dictionary<string, string[]>? Errors);
}
