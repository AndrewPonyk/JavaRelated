using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InvoiceFactoring.Infrastructure.Persistence.Repositories;

public sealed class CompanyRepository : ICompanyRepository
{
    private readonly AppDbContext _db;

    public CompanyRepository(AppDbContext db) => _db = db;

    public Task<Company?> GetByIdAsync(Guid id, CancellationToken ct = default) =>
        _db.Companies.FirstOrDefaultAsync(c => c.Id == id, ct);

    public async Task AddAsync(Company company, CancellationToken ct = default) =>
        await _db.Companies.AddAsync(company, ct);

    public void Update(Company company) => _db.Companies.Update(company);
}
