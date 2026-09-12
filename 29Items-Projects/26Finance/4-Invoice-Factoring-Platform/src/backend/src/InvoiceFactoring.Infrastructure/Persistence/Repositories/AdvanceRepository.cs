using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace InvoiceFactoring.Infrastructure.Persistence.Repositories;

public sealed class AdvanceRepository : IAdvanceRepository
{
    private readonly AppDbContext _db;

    public AdvanceRepository(AppDbContext db) => _db = db;

    public Task<Advance?> GetByIdAsync(Guid id, CancellationToken ct = default) =>
        _db.Advances.FirstOrDefaultAsync(a => a.Id == id, ct);

    public Task<Advance?> GetByInvoiceIdAsync(Guid invoiceId, CancellationToken ct = default) =>
        _db.Advances.FirstOrDefaultAsync(a => a.InvoiceId == invoiceId, ct);

    public Task<Advance?> GetByStripePayoutIdAsync(string payoutId, CancellationToken ct = default) =>
        _db.Advances.FirstOrDefaultAsync(a => a.StripePayoutId == payoutId, ct);

    public async Task AddAsync(Advance advance, CancellationToken ct = default) =>
        await _db.Advances.AddAsync(advance, ct);

    public void Update(Advance advance) => _db.Advances.Update(advance);
}
