using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InvoiceFactoring.Infrastructure.Persistence.Repositories;

public sealed class InvoiceRepository : IInvoiceRepository
{
    private readonly AppDbContext _db;

    public InvoiceRepository(AppDbContext db) => _db = db;

    public Task<Invoice?> GetByIdAsync(Guid id, CancellationToken ct = default) =>
        _db.Invoices.FirstOrDefaultAsync(i => i.Id == id, ct);

    public Task<Invoice?> GetWithAssessmentAsync(Guid id, CancellationToken ct = default) =>
        _db.Invoices.Include(i => i.Assessment).FirstOrDefaultAsync(i => i.Id == id, ct);

    public async Task<(IReadOnlyList<Invoice> Items, int TotalCount)> ListByCompanyAsync(
        Guid companyId, int page, int pageSize, CancellationToken ct = default)
    {
        var query = _db.Invoices
            .AsNoTracking()                       // read-only query (TECH-NOTES §3.6)
            .Where(i => i.CompanyId == companyId);

        var total = await query.CountAsync(ct);

        var items = await query
            .Include(i => i.Assessment)
            .OrderByDescending(i => i.CreatedAt)
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync(ct);

        return (items, total);
    }

    public async Task AddAsync(Invoice invoice, CancellationToken ct = default) =>
        await _db.Invoices.AddAsync(invoice, ct);

    public void Update(Invoice invoice) => _db.Invoices.Update(invoice);
}
