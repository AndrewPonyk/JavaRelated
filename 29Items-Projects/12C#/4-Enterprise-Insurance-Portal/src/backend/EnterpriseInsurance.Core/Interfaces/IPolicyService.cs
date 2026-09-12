using EnterpriseInsurance.Core.Entities;

namespace EnterpriseInsurance.Core.Interfaces
{
    public interface IPolicyService
    {
        Task<Policy> CreatePolicyAsync(Guid customerId, string planType, decimal initialPremium);
        Task<PagedResult<Policy>> GetAllPoliciesAsync(int page = 1, int pageSize = 10);
        Task BindPolicyAsync(Guid policyId);
        Task DeletePolicyAsync(Guid policyId);
    }
}
