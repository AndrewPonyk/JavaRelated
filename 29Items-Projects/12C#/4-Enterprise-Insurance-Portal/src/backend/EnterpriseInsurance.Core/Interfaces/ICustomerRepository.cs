using EnterpriseInsurance.Core.Entities;

namespace EnterpriseInsurance.Core.Interfaces
{
    public interface ICustomerRepository
    {
        Task<PagedResult<Customer>> GetAllAsync(int page = 1, int pageSize = 10);
        Task<Customer?> GetByIdAsync(Guid id);
        Task<Customer> AddAsync(Customer customer);
    }
}
