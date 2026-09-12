using EnterpriseInsurance.Core.Entities;
using EnterpriseInsurance.Core.Interfaces;
using Microsoft.EntityFrameworkCore;

namespace EnterpriseInsurance.Infrastructure.Data
{
    public class PolicyRepository : IPolicyRepository
    {
        private readonly InsuranceDbContext _context;

        public PolicyRepository(InsuranceDbContext context)
        {
            _context = context;
        }

        public async Task<Policy> AddAsync(Policy policy)
        {
            _context.Policies.Add(policy);
            await _context.SaveChangesAsync();
            return policy;
        }

        public async Task DeleteAsync(Guid id)
        {
            var policy = await _context.Policies.FindAsync(id);
            if (policy != null)
            {
                _context.Policies.Remove(policy);
                await _context.SaveChangesAsync();
            }
        }

        public async Task<PagedResult<Policy>> GetAllAsync(int page = 1, int pageSize = 10)
        {
            var query = _context.Policies.AsNoTracking().Include(p => p.Customer);
            var totalCount = await query.CountAsync();
            var items = await query.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync();

            return new PagedResult<Policy>
            {
                Items = items,
                TotalCount = totalCount,
                Page = page,
                PageSize = pageSize
            };
        }

        public async Task<Policy?> GetByIdAsync(Guid id)
        {
            return await _context.Policies.Include(p => p.Customer).FirstOrDefaultAsync(p => p.Id == id);
        }

        public async Task UpdateAsync(Policy policy)
        {
            _context.Entry(policy).State = EntityState.Modified;
            await _context.SaveChangesAsync();
        }
    }
}
