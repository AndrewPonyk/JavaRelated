using EnterpriseInsurance.Core.Entities;
using EnterpriseInsurance.Core.Interfaces;
using Microsoft.EntityFrameworkCore;

namespace EnterpriseInsurance.Infrastructure.Data
{
    public class CustomerRepository : ICustomerRepository
    {
        private readonly InsuranceDbContext _context;

        public CustomerRepository(InsuranceDbContext context)
        {
            _context = context;
        }

        public async Task<Customer> AddAsync(Customer customer)
        {
            _context.Customers.Add(customer);
            await _context.SaveChangesAsync();
            return customer;
        }

        public async Task<PagedResult<Customer>> GetAllAsync(int page = 1, int pageSize = 10)
        {
            var query = _context.Customers.AsNoTracking();
            var totalCount = await query.CountAsync();
            var items = await query.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync();

            return new PagedResult<Customer>
            {
                Items = items,
                TotalCount = totalCount,
                Page = page,
                PageSize = pageSize
            };
        }

        public async Task<Customer?> GetByIdAsync(Guid id)
        {
            return await _context.Customers.FindAsync(id);
        }
    }
}
