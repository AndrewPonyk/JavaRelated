using Policy.Api.Models;

namespace Policy.Api.Services;

public interface ICustomerService
{
    Task<PagedResult<CustomerDto>> ListAsync(Paging paging, CancellationToken ct);
    Task<CustomerDto?> GetByIdAsync(Guid id, CancellationToken ct);
    Task<CustomerDto> CreateAsync(CreateCustomerRequest request, CancellationToken ct);
}

public interface IQuoteService
{
    Task<PagedResult<QuoteDto>> ListAsync(string? brokerId, Paging paging, CancellationToken ct);
    Task<QuoteDto?> GetByIdAsync(Guid id, CancellationToken ct);
    Task<QuoteDto> CreateAsync(CreateQuoteRequest request, CancellationToken ct);
    Task<QuoteDto> DeclineAsync(Guid id, CancellationToken ct);
}

public interface IPolicyService
{
    Task<PagedResult<PolicyDto>> ListAsync(Guid? customerId, Paging paging, CancellationToken ct);
    Task<PolicyDetailDto?> GetByIdAsync(Guid id, CancellationToken ct);
    Task<PolicyDto> BindAsync(BindPolicyRequest request, CancellationToken ct);
    Task<PolicyDto> CancelAsync(Guid id, string reason, CancellationToken ct);
}

public interface IClaimService
{
    Task<IReadOnlyList<ClaimDto>> ListForPolicyAsync(Guid policyId, CancellationToken ct);
    Task<ClaimDto> FileAsync(Guid policyId, FileClaimRequest request, CancellationToken ct);
    Task<ClaimDto> UpdateStatusAsync(Guid claimId, UpdateClaimStatusRequest request, CancellationToken ct);
}
