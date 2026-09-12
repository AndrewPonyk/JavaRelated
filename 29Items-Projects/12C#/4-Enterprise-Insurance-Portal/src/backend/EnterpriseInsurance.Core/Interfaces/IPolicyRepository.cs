using EnterpriseInsurance.Core.Entities;

namespace EnterpriseInsurance.Core.Interfaces
{
    public interface IPolicyRepository
    {
        Task<PagedResult<Policy>> GetAllAsync(int page = 1, int pageSize = 10);
        Task<Policy?> GetByIdAsync(Guid id);
        Task<Policy> AddAsync(Policy policy);
        Task UpdateAsync(Policy policy);
        Task DeleteAsync(Guid id);
    }
}
